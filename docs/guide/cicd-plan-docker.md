CI/CD 자동화 플랜: 수동 배포 → GitHub Actions (Docker 버전)

═══════════════════════════════════════════════════════════════

Context
───────

현재 배포 방식:
- EC2: deploy.sh로 JAR scp → EC2에서 prod_start.sh 수동 실행 (source ~/env.sh → nohup java -jar ...)
- Lambda: Ldeploy.sh에 시크릿 하드코딩 → SAM deploy

문제점: 매번 수동 실행 필요, Lambda 시크릿 하드코딩 보안 이슈, EC2 환경 종속적
목표: main 머지 시 자동으로 테스트 → Docker 이미지 빌드/푸시 → EC2 + Lambda 배포

═══════════════════════════════════════════════════════════════

변경 사항 (기존 JAR 방식 → Docker 방식)
────────────────────────────────────────

[기존] JAR scp → EC2에서 nohup java -jar (env.sh로 환경변수)
[변경] Docker 이미지 빌드 → Docker Hub push → EC2에서 docker pull & run

변경 이유:
1. 과제 요구사항: "빌드된 이미지를 Docker Hub(또는 ECR)에 올리고, 클라우드 서버에서 실행"
2. 환경 재현성: 이미지만 있으면 어디서든 동일 실행 보장
3. 배포 단순화: docker pull & run 한 줄로 완료
4. 확장성: 서버 추가 시 동일 이미지 pull만 하면 됨

영향 범위:
- env.sh → 더 이상 사용하지 않음 (docker run -e로 환경변수 주입)
- prod_start.sh / dev_start.sh → 더 이상 사용하지 않음
- EC2에 Docker 설치 필요 (1회성)
- application-prod.yml의 ${DB_URL} 등 플레이스홀더 방식은 그대로 유지
  (런타임 주입 주체만 env.sh → docker run -e로 변경)

═══════════════════════════════════════════════════════════════

합의된 방향
──────────

- 트리거: main 브랜치 push (PR 머지 시)
- 이미지 레지스트리: Docker Hub (devjian/triagain)
- 시크릿: GitHub Secrets에 Docker Hub 인증 + EC2 SSH + DB 접속정보 + Lambda용 저장
- Lambda: 기존 SAM 템플릿 활용, GitHub Secrets에서 파라미터 주입 (변경 없음)

═══════════════════════════════════════════════════════════════

구현 계획
────────

0. 사전 작업 (수동 — 1회성)

  a) EC2에 Docker 설치:
     ssh -i triagain-key.pem ec2-user@3.34.132.7
     sudo yum update -y
     sudo yum install -y docker
     sudo systemctl start docker
     sudo systemctl enable docker
     sudo usermod -aG docker ec2-user
     # SSH 재접속 후 docker --version 확인

  b) Docker Hub Access Token 발급 완료 (devjian 계정, Read & Write 권한)

1. GitHub Secrets 등록 (수동 — GitHub UI에서)

┌───────────────────────┬────────────────────────────────────────────────────┐
│      Secret 이름      │                       용도                         │
├───────────────────────┼────────────────────────────────────────────────────┤
│ DOCKERHUB_USERNAME    │ Docker Hub 사용자명 (devjian)                      │
├───────────────────────┼────────────────────────────────────────────────────┤
│ DOCKERHUB_TOKEN       │ Docker Hub Access Token (Read & Write)             │
├───────────────────────┼────────────────────────────────────────────────────┤
│ EC2_SSH_KEY           │ EC2 접속용 PEM 키 (triagain-key.pem 내용 전체)     │
├───────────────────────┼────────────────────────────────────────────────────┤
│ EC2_HOST              │ EC2 IP (3.34.132.7)                                │
├───────────────────────┼────────────────────────────────────────────────────┤
│ EC2_USER              │ SSH 유저 (ec2-user)                                │
├───────────────────────┼────────────────────────────────────────────────────┤
│ DB_URL                │ jdbc:postgresql://RDS엔드포인트:5432/triagain      │
├───────────────────────┼────────────────────────────────────────────────────┤
│ DB_USERNAME           │ PostgreSQL 사용자명                                │
├───────────────────────┼────────────────────────────────────────────────────┤
│ DB_PASSWORD           │ PostgreSQL 비밀번호                                │
├───────────────────────┼────────────────────────────────────────────────────┤
│ AWS_ACCESS_KEY_ID     │ SAM deploy용 IAM 자격증명                          │
├───────────────────────┼────────────────────────────────────────────────────┤
│ AWS_SECRET_ACCESS_KEY │ SAM deploy용 IAM 자격증명                          │
├───────────────────────┼────────────────────────────────────────────────────┤
│ BACKEND_URL           │ Lambda → Backend URL (http://3.34.132.7:8080)      │
├───────────────────────┼────────────────────────────────────────────────────┤
│ INTERNAL_API_KEY      │ Lambda /internal API 인증 키                       │
└───────────────────────┴────────────────────────────────────────────────────┘

2. 프로젝트에 추가할 파일

┌──────────────────────────────────┬───────────────────────────────────────┐
│              파일                │                 작업                  │
├──────────────────────────────────┼───────────────────────────────────────┤
│ Dockerfile                       │ 신규 — 멀티스테이지 빌드 (JDK17)     │
├──────────────────────────────────┼───────────────────────────────────────┤
│ .dockerignore                    │ 신규 — 빌드 컨텍스트 제외 목록       │
├──────────────────────────────────┼───────────────────────────────────────┤
│ .github/workflows/deploy.yml    │ 신규 — 메인 배포 워크플로우           │
└──────────────────────────────────┴───────────────────────────────────────┘

3. 배포 워크플로우 구조

트리거: push to main

jobs:
  ci:
    - checkout
    - setup JDK 17 + Gradle 캐시
    - ./gradlew build (빌드 + 테스트)

  deploy-backend:
    needs: ci
    steps:
      - checkout
      - Docker Hub 로그인 (docker/login-action)
      - Docker 이미지 빌드 & 푸시 (docker/build-push-action)
        → devjian/triagain:latest + devjian/triagain:{commit-sha}
      - SSH로 EC2 접속 (appleboy/ssh-action):
          docker pull devjian/triagain:latest
          docker stop triagain || true
          docker rm triagain || true
          docker run -d \
            --name triagain \
            --restart unless-stopped \
            -p 8080:8080 \
            -e SPRING_PROFILE=prod \
            -e DB_URL=... \
            -e DB_USERNAME=... \
            -e DB_PASSWORD=... \
            devjian/triagain:latest
          # 헬스체크
          sleep 15
          curl -f http://localhost:8080/actuator/health
          docker image prune -f

  deploy-lambda:
    needs: ci
    조건: lambda/ 디렉토리 변경 시에만 실행 (dorny/paths-filter 사용)
    steps:
      - checkout
      - setup AWS credentials (aws-actions/configure-aws-credentials)
      - setup SAM CLI (aws-actions/setup-sam)
      - sam build --template-file lambda/template.yaml
      - sam deploy (GitHub Secrets에서 파라미터 주입)

4. Dockerfile 상세

  멀티스테이지 빌드:
  - Stage 1 (builder): gradle:8.5-jdk17 → gradlew bootJar
  - Stage 2 (runtime): eclipse-temurin:17-jre-alpine → java -jar app.jar
  - 환경변수: SPRING_PROFILE (기본값 prod)
  - 타임존: Asia/Seoul

  application-prod.yml과의 연동:
  - yml 파일의 ${DB_URL}, ${DB_USERNAME}, ${DB_PASSWORD} 플레이스홀더는 그대로 유지
  - docker run -e DB_URL=... 으로 런타임 주입
  - Spring Boot가 환경변수를 자동으로 바인딩

═══════════════════════════════════════════════════════════════

환경변수 매핑 (env.sh → docker run -e)
──────────────────────────────────────

기존 env.sh:
  export DB_URL=jdbc:postgresql://...
  export DB_USERNAME=triagain
  export DB_PASSWORD=xxxxx

변경 후 docker run:
  docker run -d \
    -e DB_URL=jdbc:postgresql://... \
    -e DB_USERNAME=triagain \
    -e DB_PASSWORD=xxxxx \
    devjian/triagain:latest

application-prod.yml은 변경 없음. 주입 방식만 바뀜.

═══════════════════════════════════════════════════════════════

기존 테스트 워크플로우
─────────────────────

- .github/workflows/test.yml — PR 시 테스트 (기존 그대로 유지)
- .github/workflows/deploy.yml — main 머지 시 테스트 + 배포 (신규)

═══════════════════════════════════════════════════════════════

기존 파일 처리
─────────────

┌──────────────────────┬────────────────────────────────────────────┐
│        파일          │                  처리                      │
├──────────────────────┼────────────────────────────────────────────┤
│ deploy.sh            │ 유지 (수동 백업용, 더 이상 사용하지 않음)  │
├──────────────────────┼────────────────────────────────────────────┤
│ prod_start.sh        │ 유지 (수동 백업용, Docker 전환 후 불필요)  │
├──────────────────────┼────────────────────────────────────────────┤
│ dev_start.sh         │ 유지 (수동 백업용)                         │
├──────────────────────┼────────────────────────────────────────────┤
│ ~/env.sh (EC2)       │ 유지 (수동 롤백 시 사용 가능)              │
├──────────────────────┼────────────────────────────────────────────┤
│ lambda/Ldeploy.sh    │ 유지 (수동 백업용)                         │
├──────────────────────┼────────────────────────────────────────────┤
│ lambda/deploy-lambda │ 유지 (수동 백업용)                         │
└──────────────────────┴────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════

검증 방법
────────

1. feature 브랜치에서 Dockerfile + .dockerignore + deploy.yml 커밋
2. main으로 PR 생성 → CI(빌드/테스트)만 실행되는지 확인
3. PR 머지 → CD(Docker 빌드 → Docker Hub 푸시 → EC2 배포) 실행 확인
4. GitHub Actions 탭에서 성공 로그 스크린샷 캡처 (과제 제출물)
5. EC2에서 docker ps로 컨테이너 실행 확인
6. curl http://3.34.132.7:8080/actuator/health 응답 확인
7. Lambda 변경 시에만 SAM deploy 실행되는지 확인

═══════════════════════════════════════════════════════════════

수동 롤백 방법
─────────────

# 이전 이미지로 롤백
docker stop triagain && docker rm triagain
docker run -d --name triagain --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILE=prod \
  -e DB_URL=... \
  devjian/triagain:{이전-commit-sha}

# 또는 Docker 이전으로 완전 롤백 (비상 시)
source ~/env.sh
nohup java -jar ~/triagain-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod > ~/log/server.log 2>&1 &

═══════════════════════════════════════════════════════════════

과제 제출물 체크리스트
────────────────────

✔ Live Server URL: http://3.34.132.7:8080 (또는 Swagger UI 주소)
✔ GitHub Actions YML: .github/workflows/deploy.yml
✔ Actions 성공 로그 스크린샷: GitHub → Actions 탭에서 캡처
