# 배포 파이프라인 가이드

## 1. 개요

GitHub Actions 기반 CI/CD 파이프라인으로, **main 브랜치 push 시 자동 배포**, PR 시 CI만 실행한다.

### 트리거 조건

| 이벤트 | CI (빌드+테스트) | Backend 배포 | Lambda 배포 |
|--------|:-:|:-:|:-:|
| PR → main | O | X | X |
| Push → main | O | O | 조건부 (`lambda/**` 변경 시) |

### 파이프라인 전체 흐름

```
Push to main
│
├─ ci ─────────────────────────────────────────────────┐
│  Gradle Build + Test → JUnit Report → Cucumber Report │
└──────────────────────────────────────────────────────┘
     │                            │
     ▼                            ▼
┌─ deploy-backend ──────┐  ┌─ check-lambda-changes ──┐
│  Docker Build & Push   │  │  lambda/** 변경 감지     │
│  EC2 SSH → Pull & Run  │  └────────┬───────────────┘
│  Health Check          │           │ (변경 있으면)
└────────────────────────┘           ▼
                          ┌─ deploy-lambda ──────────┐
                          │  SAM Build → SAM Deploy   │
                          │  (CloudFormation stack)    │
                          └───────────────────────────┘
```

---

## 2. CI Job

모든 push/PR에서 실행된다.

### 단계

1. **Checkout** — `actions/checkout@v4`
2. **JDK 17 설정** — `temurin` 배포판
3. **Gradle Setup** — `gradle/actions/setup-gradle@v4`
4. **빌드 + 테스트** — `./gradlew build`
5. **JUnit 리포트 발행** — `dorny/test-reporter@v1` (PR Checks 탭에 표시)
6. **Cucumber 리포트 업로드** — `actions/upload-artifact@v4` (14일 보관)

### 권한

```yaml
permissions:
  contents: read   # 코드 체크아웃
  checks: write    # JUnit 테스트 리포트 발행
```

---

## 3. Backend 배포 (Docker + EC2)

CI 통과 후 `push` 이벤트에서만 실행 (`needs: ci`, `if: github.event_name == 'push'`).

### Docker 이미지 빌드

멀티스테이지 Dockerfile:

| Stage | Base Image | 역할 |
|-------|-----------|------|
| builder | `gradle:8.5-jdk17` | `./gradlew bootJar` 실행 |
| runtime | `eclipse-temurin:17-jre-alpine` | JAR 실행 (tzdata + curl 포함, Asia/Seoul) |

### 이미지 Push

Docker Hub `devjian/triagain`에 두 태그로 push:
- `latest` — 현재 운영 버전
- `${github.sha}` — 커밋별 고정 태그 (롤백용, 추후 활용 예정)

### EC2 배포

`appleboy/ssh-action@v1`으로 EC2에 SSH 접속 후 실행:

```bash
docker pull devjian/triagain:latest
docker stop triagain || true && docker rm triagain || true
docker run -d --name triagain --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e "DB_URL=$DB_URL" \
  -e "DB_USERNAME=$DB_USERNAME" \
  -e "DB_PASSWORD=$DB_PASSWORD" \
  -e "JWT_SECRET=$JWT_SECRET" \
  -e "INTERNAL_API_KEY=$INTERNAL_API_KEY" \
  devjian/triagain:latest
sleep 15
curl -f http://localhost:8080/actuator/health
docker image prune -f
```

- 15초 대기 후 `/actuator/health` 헬스체크
- 이전 이미지 자동 정리 (`docker image prune -f`)

### 필요 시크릿

| 시크릿 | 용도 |
|--------|------|
| `DOCKERHUB_USERNAME` | Docker Hub 로그인 |
| `DOCKERHUB_TOKEN` | Docker Hub 액세스 토큰 |
| `EC2_HOST` | EC2 퍼블릭 IP/도메인 |
| `EC2_USER` | EC2 SSH 사용자 (ec2-user) |
| `EC2_SSH_KEY` | EC2 SSH 프라이빗 키 |
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (Base64, 256bit+) |
| `INTERNAL_API_KEY` | `/internal/**` API 인증 키 |

---

## 4. Lambda 배포 (SAM)

### 변경 감지

`check-lambda-changes` job이 `dorny/paths-filter@v3`로 `lambda/**` 경로 변경을 감지한다.
변경이 없으면 `deploy-lambda` job은 스킵된다.

### 배포 단계

1. **AWS 자격증명 설정** — `aws-actions/configure-aws-credentials@v4` (리전: `ap-northeast-2`)
2. **SAM CLI 설치** — `aws-actions/setup-sam@v2`
3. **SAM Build** — `sam build --template-file lambda/template.yaml`
4. **SAM Deploy** — CloudFormation stack `triagain-lambda`에 배포

### Lambda 역할

S3 `triagain-verifications` 버킷의 `upload-sessions/` 경로에 PutObject 이벤트 발생 시:

```
S3 PutObject → Lambda (triagain-upload-complete)
  → PUT /internal/upload-sessions/complete?imageKey={key}
  → Spring Boot가 upload session COMPLETED 처리 + SSE 이벤트 발행
```

- Runtime: Python 3.12
- Timeout: 15초, Memory: 128MB
- 인증: `X-Internal-Api-Key` 헤더

### SAM 템플릿 파라미터

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `BackendUrl` | (필수) | Spring Boot API URL |
| `InternalApiKey` | (필수, NoEcho) | Internal API 인증 키 |
| `S3BucketName` | `triagain-verifications` | S3 버킷 이름 |

### 필요 시크릿

| 시크릿 | 용도 |
|--------|------|
| `AWS_ACCESS_KEY_ID` | AWS IAM 액세스 키 |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM 시크릿 키 |
| `BACKEND_URL` | Spring Boot API URL |
| `INTERNAL_API_KEY` | Internal API 인증 키 (Backend와 공유) |

---

## 5. 필요 GitHub Secrets 전체 목록

### Backend 배포

| 시크릿 | 용도 |
|--------|------|
| `DOCKERHUB_USERNAME` | Docker Hub 로그인 |
| `DOCKERHUB_TOKEN` | Docker Hub 액세스 토큰 |
| `EC2_HOST` | EC2 퍼블릭 IP/도메인 |
| `EC2_USER` | EC2 SSH 사용자 |
| `EC2_SSH_KEY` | EC2 SSH 프라이빗 키 |
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 |
| `INTERNAL_API_KEY` | Internal API 인증 키 |

### Lambda 배포

| 시크릿 | 용도 |
|--------|------|
| `AWS_ACCESS_KEY_ID` | AWS IAM 액세스 키 |
| `AWS_SECRET_ACCESS_KEY` | AWS IAM 시크릿 키 |
| `BACKEND_URL` | Spring Boot API URL |
| `INTERNAL_API_KEY` | Internal API 인증 키 (Backend와 공유) |

> `INTERNAL_API_KEY`는 Backend, Lambda 양쪽에서 공유한다.

---

## 6. 로컬 개발 환경

### PostgreSQL 실행

`docker-compose.yml`로 PostgreSQL 16을 로컬에서 실행한다.

```bash
docker compose up -d
```

| 항목 | 값 |
|------|-----|
| 이미지 | `postgres:16-alpine` |
| 포트 | `5432:5432` |
| DB | `triagain` |
| 사용자 | `triagain` |
| 비밀번호 | `triagain` |
| 볼륨 | `triagain-postgres-data` (데이터 영속화) |

### application-local.yml 프로파일

기본 활성 프로파일은 `local`이다 (`application.yml`에서 `spring.profiles.active: local` 설정).

| 항목 | local | prod |
|------|-------|------|
| DB 접속 | `localhost:5432/triagain` | `${DB_URL}` 환경변수 |
| DDL 전략 | `update` (자동 스키마 갱신) | `validate` (검증만) |
| SQL 로깅 | `show-sql: true` + format + bind trace | `show-sql: false` |
| Flyway | `enabled: false` | baseline-on-migrate |
| JWT Secret | 하드코딩 기본값 | `${JWT_SECRET}` 환경변수 |

---

## 7. 수동 배포 (비상용)

### Backend — deploy.sh (레거시)

JAR를 직접 EC2로 전송하는 방식. Docker 도입 전 사용하던 스크립트.

```bash
./deploy.sh
# 1. ./gradlew bootJar
# 2. scp로 JAR → EC2 전송
# 3. EC2에서 ~/dev_start.sh 또는 ~/prod_start.sh 수동 실행
```

> 현재는 GitHub Actions + Docker 파이프라인을 사용하므로, 비상시에만 참고.

### Lambda — deploy-lambda.sh (수동 SAM 배포)

```bash
cd lambda
./deploy-lambda.sh <backend-url> <internal-api-key> [s3-bucket-name]

# 예시:
./deploy-lambda.sh https://api.triagain.com my-secret-key
```

이 스크립트는 GitHub Actions의 SAM 배포에 추가로 다음을 수행한다:
- S3 버킷에 Lambda notification 설정 (`s3:ObjectCreated:Put`, prefix: `upload-sessions/`)
- Lambda에 S3 invoke 권한 부여

> 최초 S3 → Lambda 연결 시 이 스크립트로 notification을 설정해야 한다. GitHub Actions의 SAM deploy만으로는 S3 notification이 설정되지 않는다.

---

## 8. 주의사항 / 추후 고려

### 현재 제한

- **SHA 태그 롤백 미구현**: Docker Hub에 SHA 태그 이미지가 보관되지만, 롤백 workflow는 아직 없다. 현재는 `latest` 태그만 사용.
- **`/internal/**` 네트워크 접근 제한 필요**: `permitAll()`로 설정되어 있으며, `X-Internal-Api-Key` 헤더로만 인증한다. AWS VPC Security Group으로 Lambda → API 서버만 접근 허용 필요.
- **CORS 설정 미적용**: Spring 레벨 CORS 설정이 없다. 프론트엔드 도메인 허용 필요.
- **SSL/HTTPS 미설정**: ALB 또는 EC2 레벨에서 설정 필요.
- **DB 마이그레이션**: `ddl-auto: validate`이므로 스키마 변경 시 수동 마이그레이션 또는 Flyway 활용 필요.
- **헬스체크 단순화**: `sleep 15` 후 1회 `curl` — 실패 시 자동 롤백 없음.

### 참조 문서

| 문서 | 경로 |
|------|------|
| CI/CD 워크플로우 | `.github/workflows/deploy.yml` |
| Dockerfile | `Dockerfile` |
| 로컬 PostgreSQL | `docker-compose.yml` |
| SAM Lambda 템플릿 | `lambda/template.yaml` |
| 수동 Lambda 배포 | `lambda/deploy-lambda.sh` |
| Lambda 핸들러 | `lambda/upload-complete/handler.py` |
| 프로덕션 배포 체크리스트 | `docs/prod-deploy-checklist.md` |
| 프로덕션 프로파일 | `src/main/resources/application-prod.yml` |
