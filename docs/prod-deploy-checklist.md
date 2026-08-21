# 운영 배포 체크리스트

> 2026-08-20 저장소 기준. 설정·워크플로·Dockerfile에서 확인한 사실과 저장소 밖 운영 확인을
> 구분한다. 체크 표시가 없는 항목은 코드 미구현을 뜻하지 않고 배포 때 확인해야 하는 작업일 수 있다.

---

## 1. 프로필별 실제 설정

| 구분 | DB·DDL | 보안·스토리지 | 비고 |
|---|---|---|---|
| 공통 `application.yml` | datasource 없음, Flyway baseline 설정, `ddl-auto` 없음 | JWT·Apple 로컬 기본값 | 기본 active profile은 `local` |
| 개인 `local` | 저장소 정본 없음 | `!prod` Bean 사용 | `application-local.yml`은 `.gitignore` 대상이며 현재 추적 파일에 없음 |
| `dev` | PostgreSQL 환경변수, `ddl-auto: validate`, Flyway 공통값 상속 | DevSecurity + LocalStorage | Firebase 속성 미설정 시 No-op |
| `prod` | PostgreSQL 환경변수, `ddl-auto: validate`, Flyway 공통값 상속 | JWT + Internal API Key + S3 | FCM 조건부, 첫 인증 알림 기본 ON |
| `test` | H2, Flyway OFF, `create-drop` | `!prod` Bean | SQL 출력 ON |
| `integration` | Testcontainers가 datasource를 동적 주입 | `!prod` Bean | Flyway ON, 각 테스트가 DDL 설정을 추가할 수 있음 |

### 현재 확인된 차이

- 과거 문서의 “로컬 `ddl-auto:update`” 설명은 현재 추적 파일로 확인되지 않는다.
- `application.yml`은 `local`을 기본 활성화하지만 저장소에는 datasource를 제공하는
  `application-local.yml`이 없다. 개인 로컬 파일이 따로 있을 수 있으나 정본으로 간주하지 않는다.
- Dockerfile은 `SPRING_PROFILES_ACTIVE=prod`를 지정하고 배포 명령도 같은 값을 다시 전달한다.
- 컨테이너의 `/etc/localtime`과 `/etc/timezone`은 `Asia/Seoul`이다. 이 값은 로그 표시뿐 아니라
  `Clock.systemDefaultZone()` 및 PostgreSQL 세션 시간 기준에 영향을 주므로 임의 변경하지 않는다.

---

## 2. GitHub Actions 실행 조건

워크플로: `.github/workflows/deploy.yml`

| 이벤트 | 브랜치 | 실행 잡 | 잡 의존 관계 |
|---|---|---|---|
| Pull Request | `main`, `develop` | `ci` → `e2e` | `e2e`가 `ci`를 기다림. 배포 잡은 실행되지 않음 |
| Push | `main`, `develop` | `deploy-backend`, `check-lambda-changes` | 백엔드 배포가 테스트 잡을 기다리지 않음 |
| Push + `lambda/**` 변경 | `main`, `develop` | 위 잡 + `deploy-lambda` | Lambda 배포는 변경 감지 잡만 기다림 |

### 확인된 배포 게이트 공백

- `deploy-backend`에는 `needs: ci` 또는 `needs: e2e`가 없다.
- `ci`와 `e2e`는 `pull_request`에서만 실행된다.
- 따라서 push 경로에서 자동 실행되는 테스트는 0개다.
- `develop` push도 `main`과 같은 Docker Hub 저장소, `latest` 태그, EC2 host, `prod` 프로필로 배포된다.
- 브랜치 보호와 required check는 저장소 밖 GitHub 설정이다. Actions YAML만으로는 보호 상태를
  보장할 수 없다.

이 문서는 현행 위험을 기록할 뿐 배포 브랜치·게이트 정책을 임의로 변경하지 않는다.

---

## 3. 백엔드 배포가 실제로 하는 일

### Docker 이미지 생성

1. Docker Hub 로그인
2. Dockerfile builder에서 `./gradlew bootJar --no-daemon` 실행
3. 다음 태그 두 개 push
   - `devjian/triagain:latest`
   - `devjian/triagain:${GITHUB_SHA}`

`bootJar`는 애플리케이션 JAR를 만들지만 이 Docker build 경로에는 `checkstyle`, `test`, `e2eTest`
게이트가 없다.

### EC2 교체 배포

1. Firebase 서비스 계정 파일이 존재하는지만 확인해 `FIREBASE_ENABLED` 결정
2. `devjian/triagain:latest` pull
3. 기존 `triagain` 컨테이너 stop·remove
4. 새 `triagain` 컨테이너 run
5. 15초 대기
6. `curl -f http://localhost:8080/actuator/health`
7. dangling Docker image prune

### 현재 배포 특성

- 실제 실행 이미지는 SHA 태그가 아니라 `latest`다.
- 기존 컨테이너를 먼저 중단하므로 무중단 배포가 아니다.
- 새 컨테이너의 health check가 실패해도 이전 컨테이너를 자동 복구하는 rollback 단계가 없다.
- SHA 이미지는 push되지만 자동 rollback·재배포에 사용되지 않는다.
- build context 제외 주체는 Dockerfile이 아니라 `.dockerignore`다 (Dockerfile은 `COPY . .`을 쓴다).
  `.dockerignore`가 제외하는 범주: Git 메타(`.git`, `.gitignore`), 빌드 산출물(`.gradle`, `build`),
  IDE·에디터 파일, 문서(`docs/`), Lambda(`lambda/`), GitHub Actions(`.github/`),
  로컬 비밀 파일(`.env`, `*.pem`, `triagain-admin_accessKeys.csv`), OS 부산물,
  스크립트(`deploy.sh`, `*.bak`), 에이전트 설정(`.claude/`, `CODEX.md`, `agent`).
- 컨테이너 로그는 `awslogs` 드라이버로 `/triagain/app`에 보낸다. EC2의 CloudWatch Logs 권한과
  로그 그룹 생성 권한은 저장소 밖 IAM에서 확인해야 한다.

---

## 4. 환경변수와 외부 자격 증명

### 백엔드 부팅 필수

| 변수 | 사용처 | 미설정 영향 |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | prod datasource 생성 실패 |
| `DB_USERNAME` | PostgreSQL 사용자 | DB 연결 실패 |
| `DB_PASSWORD` | PostgreSQL 비밀번호 | DB 연결 실패 |
| `JWT_SECRET` | Access·Refresh JWT 서명 | prod placeholder 해석 실패 또는 부팅 실패 |
| `APPLE_REFRESH_KEY` | Apple refresh token AES-256-GCM 암복호화 | prod placeholder 해석 실패 또는 부팅 실패 |
| `INTERNAL_API_KEY` | `/internal/**` 운영 필터 | prod placeholder 해석 실패 또는 부팅 실패 |

### 기능별 조건부 값

| 변수·외부 값 | 현재 기본값 | 적용 조건 |
|---|---|---|
| `APPLE_CLIENT_ID` | `com.triagain.triagain` | 다른 Apple Service ID를 사용하면 명시 |
| `APPLE_TEAM_ID` | blank | Apple authorization code 교환·revoke 사용 시 아래 3개 모두 필요 |
| `APPLE_KEY_ID` | blank | 위와 같음 |
| `APPLE_PRIVATE_KEY` | blank | 위와 같음. 일부만 설정하면 부팅 실패 |
| `FIREBASE_ENABLED` | `false` | 실제 FCM을 사용할 때 `true` |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | `/opt/triagain/config/triagain-firebase-service-account.json` | Firebase 활성 시 유효한 JSON 파일 필요 |
| `CREW_FIRST_VERIFICATION_ENABLED` | `true` | 첫 인증 알림 긴급 OFF 시 `false` |

### 배포 워크플로 전달 상태

- DB 3종, JWT, Internal API Key, Apple Team/Key/Private/Refresh 값은 GitHub Secrets에서 EC2로 전달한다.
- `APPLE_CLIENT_ID`는 전달하지 않고 애플리케이션 기본값을 사용한다.
- `CREW_FIRST_VERIFICATION_ENABLED`는 전달하지 않아 prod 기본값 `true`를 사용한다.
- Firebase 활성 여부는 GitHub Secret이 아니라 EC2 파일 존재 여부로 결정한다.
- S3 bucket과 region은 `application-prod.yml`에 각각 `triagain-verifications`, `ap-northeast-2`로
  고정되어 있다.
- S3 SDK 자격 증명은 컨테이너 환경변수로 전달하지 않는다. EC2 Instance Profile 등 AWS SDK
  기본 credential chain의 실제 공급원을 저장소 밖에서 확인해야 한다.

### 비밀 관리 경계

- Firebase JSON은 EC2의 `/home/ec2-user/triagain/config`를 read-only mount하여 사용한다.
- Apple private key와 DB/JWT/Internal key는 컨테이너 환경변수로 전달된다.
- 같은 호스트의 충분한 권한을 가진 사용자는 컨테이너 설정이나 프로세스 환경에서 값을 볼 수 있다.
- Secrets Manager·Parameter Store 런타임 조회와 무중단 키 회전은 현재 구현되어 있지 않다.

---

## 5. DB와 Flyway

- 운영은 PostgreSQL이며 `ddl-auto: validate`다.
- Flyway는 공통 설정을 상속해 활성화된다.
- `baseline-on-migrate: true`, `baseline-version: 6`이다.
- 마이그레이션 정본은 `src/main/resources/db/migration/V1...V26`이다.
- 별도 migration job은 없으며 새 애플리케이션이 부팅될 때 Flyway가 실행된다.
- migration 또는 `validate` 실패 시 새 컨테이너 health check가 실패하지만 자동 rollback은 없다.
- H2 test 프로필은 Flyway를 끄므로 PostgreSQL 전용 DDL 검증을 대신하지 못한다.

### 배포 전 DB 확인

- [ ] 적용 대상 DB와 현재 `flyway_schema_history` 버전 확인
- [ ] 신규 migration의 운영 PostgreSQL 호환성과 롤백/복구 방법 확인
- [ ] 파괴적·대용량 DDL이면 백업과 예상 lock 시간을 확인
- [ ] baseline이 필요한 기존 DB인지, 신규 빈 DB인지 구분
- [ ] 배포 후 Flyway 완료와 Hibernate `validate` 통과 확인

---

## 6. HTTP·보안 경계

### 저장소에서 확인됨

- [x] 운영에서 `XUserIdAuthenticationFilter` 미등록
- [x] 나머지 보호 API는 Bearer JWT와 DB `tokenVersion` 검증
- [x] `/internal/**`는 운영에서 `InternalApiKeyFilter`가 `X-Internal-Api-Key` 검증
- [x] `/health`, `/actuator/health` 공개
- [x] Swagger UI와 `/v3/api-docs/**` 운영 공개
- [x] `/upload-sessions/*/events` 운영 공개 — 현재 SSE 인증·소유권 구현 전
- [x] `server.forward-headers-strategy: framework` 설정됨
- [x] Spring 전역 CORS 설정 없음

### 운영에서 확인할 사항

- [ ] Lambda와 백엔드에 동일한 `INTERNAL_API_KEY` 주입
- [ ] `/internal/**`에 네트워크/WAF/rate-limit 등 추가 접근 제한이 필요한지 결정
- [ ] Swagger·OpenAPI 운영 공개 유지 여부 결정
- [ ] SSE JWT 인증·세션 소유권 확인 구현 전 공개 경로 위험 수용 여부 확인
- [ ] nginx/ALB가 `X-Forwarded-Proto`, `X-Forwarded-Host`를 올바르게 전달하는지 확인
- [ ] Flutter Web 또는 브라우저 클라이언트를 제공할 때만 Spring·S3 CORS 허용 origin 확정

Flutter iOS/Android 네이티브 HTTP 클라이언트에는 브라우저 CORS 정책이 적용되지 않는다. 따라서
“CORS 설정”을 모든 모바일 배포의 필수 완료 항목으로 단정하지 않는다.

---

## 7. Health check와 관측

| 경로 | 현재 의미 |
|---|---|
| `/actuator/health` | 배포 워크플로가 `curl -f`로 확인하는 Spring Boot health |
| `/health` | 항상 HTTP 200이며 body의 `database`가 `UP` 또는 `DOWN` |

- 배포 성공 판정은 현재 `/actuator/health` HTTP 상태만 본다.
- 애플리케이션 로그는 CloudWatch Logs `/triagain/app`, stream `triagain`으로 고정한다.
- 컨테이너 로그는 `awslogs` 드라이버로 **항상** CloudWatch에 수집된다(위 참조). 별개로,
  **배포 workflow에는 실패 시 컨테이너 로그를 출력하는 단계(`docker logs`)와 이전 버전 rollback 단계가 없다.**
  실패 원인은 CloudWatch에서 직접 확인해야 한다.
- 알림, Lambda 실패, Dead Letter 적재량에 대한 저장소 내 CloudWatch alarm 정의는 없다.

### 배포 후 확인

- [ ] `/actuator/health`가 정상이고 DB health가 포함되는지 확인
- [ ] `/health` body의 `database=UP` 확인
- [ ] CloudWatch Logs에 새 컨테이너 로그 유입 확인
- [ ] 주요 로그인·조회 API 최소 스모크 확인
- [ ] 스케줄러 중복 실행 여부 확인(다중 컨테이너가 동시에 살아 있지 않은지 포함)

---

## 8. Lambda·S3 업로드 완료 경로

### GitHub Actions 자동 배포 범위

- `lambda/**` 변경 push를 `dorny/paths-filter`로 감지한다.
- SAM build와 CloudFormation deploy를 실행한다.
- workflow는 `BackendUrl`, `InternalApiKey`만 전달하며 `S3BucketName`은 템플릿 기본값을 사용한다.
- SAM 템플릿에는 S3 Event가 없으므로 workflow 자체는 새 버킷 알림과 Lambda invoke 권한을 만들지 않는다.

### 수동 `lambda/deploy-lambda.sh` 범위

- SAM 배포 후 Lambda에 S3 invoke permission을 추가한다.
- 버킷에 `s3:ObjectCreated:Put`, prefix `upload-sessions/` 알림을 설정한다.
- `put-bucket-notification-configuration`에 이 Lambda 구성만 전달하므로 같은 버킷의 기존 다른
  notification 구성을 포함해 보존하는 병합 로직이 없다.
- Internal API Key를 두 번째 명령행 인자로 받으므로 셸 히스토리·프로세스 인자 노출을 고려해야 한다.

### 현재 템플릿 경계

- Lambda runtime `python3.12`, timeout 15초, memory 128MB
- 백엔드 HTTP timeout 10초
- DLQ, 실패 Destination, 명시적 비동기 retry, CloudWatch alarm 정의 없음
- Lambda role에 `s3:GetObject` 권한이 있지만 현재 handler는 오브젝트를 읽지 않고 이벤트 key만 사용

### 운영 확인

- [ ] 실제 S3 bucket notification에 Lambda ARN·`ObjectCreated:Put`·prefix가 등록됐는지 확인
- [ ] Lambda resource policy에 해당 bucket invoke 권한이 있는지 확인
- [ ] `BACKEND_URL`이 운영 HTTPS 주소이며 Lambda에서 도달 가능한지 확인
- [ ] Lambda·백엔드 API Key 일치 확인
- [ ] 실패 retry·DLQ·경보의 실제 AWS 설정 확인
- [ ] 다른 S3 notification이 있다면 수동 스크립트 실행 전 병합·보존 계획 확인
- [ ] 실제 파일 업로드 후 세션이 `COMPLETED`로 바뀌는 end-to-end 스모크 확인

---

## 9. Firebase·알림 운영

- 배포 워크플로는 Firebase JSON의 **존재만** 보고 FCM을 활성화한다.
- JSON 파싱은 부팅 때 수행하지만 자격 증명의 실제 유효성은 첫 Firebase 호출 전까지 보장되지 않는다.
- 배포 워크플로는 `POST /internal/fcm-test`를 호출하지 않는다.
- FCM 스모크 API는 발송 실패도 HTTP 200으로 반환하므로 `data.status == SUCCESS`를 확인해야 한다.
- 실사용자 토큰 대신 운영 전용 카나리 디바이스 토큰을 사용한다.
- `CREW_FIRST_VERIFICATION_ENABLED`는 workflow에서 전달하지 않아 prod 기본 ON이다.
- 사용자별 알림 종류·시간대·방해 금지 규칙은 아직 제품 확정 전이다.
- `AsyncConfig`에는 `@Async` 미처리 예외 로거가 구현되어 있다.
- 첫 인증 리스너의 실제 commit/rollback 동작은 기존 테스트로 검증되어 있으나, 수신자 조회
  네이티브 SQL의 첫 인증 전용 조건은 아직 전용 실DB 테스트가 없다.

### 배포 후 확인

- [ ] `FIREBASE_ENABLED`의 실제 컨테이너 값 확인
- [ ] Firebase 서비스 계정 파일 존재·권한·JSON 파싱 확인
- [ ] 카나리 토큰으로 `/internal/fcm-test` 호출 후 `data.status == SUCCESS` 확인
- [ ] 첫 인증 알림을 즉시 끌 수 있는 운영 환경변수 전달 경로 확정
- [ ] 첫 인증 대상 네이티브 SQL의 본인 제외·ACTIVE 멤버 조건 실DB 검증 추가
- [ ] 알림 on/off·발송 시간 제품 정책 확정 후 성공·실패 알림 활성 여부 결정

---

## 10. 저장소 밖 운영 항목

아래는 과거 운영 기록이 문서에 있어도 현재 상태를 저장소만으로 재검증할 수 없다.

- nginx TLS 종료와 8080 reverse proxy
- Let's Encrypt 인증서 경로와 `certbot-renew.timer`
- Route 53/DNS, EC2 Security Group, WAF
- RDS 백업·복구·maintenance 설정
- EC2 Instance Profile의 S3·CloudWatch Logs 권한
- GitHub branch protection과 required checks
- Docker Hub·EC2·AWS·Apple·Firebase Secret의 실제 값과 만료 상태
- Lambda retry·DLQ·CloudWatch alarm
- S3 CORS와 bucket notification의 실제 운영 값

### 마지막으로 기록된 수동 인프라 상태 — 재확인 필요

아래 값은 기존 운영 문서의 기록을 보존한 것이며 이번 저장소 대조로 현재 상태를 확인한 것은 아니다.

- EC2 host nginx가 443 TLS를 종료하고 앱 컨테이너 8080으로 reverse proxy
- Let's Encrypt 인증서 기록 경로:
  - `/etc/letsencrypt/live/api.triagain.kr/`
  - `/etc/letsencrypt/live/triagain.kr/`
- `certbot-renew.timer`는 2026-07-08 활성화 기록이 있다. 그 전 비활성 상태로 인증서 만료 장애가
  있었으므로 `sudo certbot certificates`와 timer 상태를 주기적으로 확인한다.
- 초대 OG 변경 후 [카카오 디벨로퍼스 OG 캐시 초기화](https://developers.kakao.com/tool/clear/og)가
  필요하다. 이미지 query만 바꾸는 것으로는 페이지 URL 기준 캐시가 갱신되지 않을 수 있다.

### 최종 배포 전 체크

- [ ] 배포 대상이 `main`인지 `develop`인지와 둘 다 같은 운영 EC2로 가는 현행을 인지
- [ ] PR의 `CI (Unit + Cucumber)`와 `E2E Tests` 성공 확인
- [ ] Docker Hub·EC2·애플리케이션 필수 Secrets 확인
- [ ] DB migration·백업 확인
- [ ] Firebase·Apple·AWS 외부 자격 증명 유효성 확인
- [ ] 현재 운영 이미지 식별 정보 기록(SHA 태그는 push되지만 실행은 `latest`)
- [ ] 장애 시 되돌릴 SHA 이미지와 수동 rollback 명령 준비
- [ ] 배포 후 health·로그·핵심 API·업로드 완료·FCM 스모크 확인
