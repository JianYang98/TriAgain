# Lambda 배포 가이드 — Round 3 (2026-03-13)

> 브랜치: `feat/s3-lambda-presigned-url`
> 목표: Lambda 배포 + S3 → Lambda → Backend → SSE 전체 플로우 연결

---

## 이전 라운드

이 가이드는 [첫 배포 가이드](20260306-first-deploy-guide.md)에 이어지는 Round 3이다.

| Round | 내용 | 상태 |
|-------|------|------|
| Round 1 | dev 프로필 기본 API 검증 | 완료 |
| Round 2 | prod 프로필 S3 Presigned URL 검증 | 완료 |
| **Round 3** | **Lambda 배포 + 전체 플로우 연결** | **이번 가이드** |

---

## 현재 상태

| 구성 요소 | 코드 | 배포 |
|-----------|------|------|
| Backend (Internal API, SSE, InternalApiKeyFilter) | ✅ | ✅ (EC2) |
| S3 버킷 (`triagain-verifications`) | - | ✅ (생성 완료, CORS 완료) |
| Lambda handler (`handler.py`) | ✅ | ❌ |
| SAM template (`template.yaml`) | ✅ | ❌ |
| 배포 스크립트 (`deploy-lambda.sh`) | ✅ | ❌ |
| S3 → Lambda 이벤트 알림 | - | ❌ |
| `INTERNAL_API_KEY` 환경변수 (EC2 + Lambda) | - | ❌ |

---

## AWS 리소스

| 리소스 | 값 |
|--------|-----|
| EC2 | `3.34.132.7` (t3.micro, 서울, 포트 22/80/443/8080) |
| RDS | `triagain-db.cxis2q4z2sto.ap-northeast-2.rds.amazonaws.com` |
| S3 | `triagain-verifications` (서울, 퍼블릭 차단, CORS 완료) |
| Region | `ap-northeast-2` |

---

## 전체 플로우

```
Client → POST /upload-sessions → Presigned URL 반환
Client → PUT (Presigned URL) → S3 업로드
S3 PutObject 이벤트 → Lambda 트리거
Lambda → PUT /internal/upload-sessions/complete → Backend
Backend → SSE 이벤트 "upload-complete" → Client
Client → POST /verifications (sessionId) → 인증 생성
```

---

## Step 1: INTERNAL_API_KEY 생성

로컬에서 시크릿 키 생성:

```bash
openssl rand -hex 32
# 결과 예: a1b2c3d4e5f6... (64자 hex)
```

이 키를 EC2와 Lambda 양쪽에서 동일하게 사용한다. 안전한 곳에 메모.

---

## Step 2: EC2 환경변수에 INTERNAL_API_KEY 추가

```bash
ssh -i triagain-key.pem ec2-user@3.34.132.7

# env.sh에 INTERNAL_API_KEY 추가
echo 'export INTERNAL_API_KEY=<Step1에서 생성한 키>' >> ~/env.sh
```

> `env.sh`에 기존 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`이 이미 있다 (Round 1에서 설정).

---

## Step 3: Backend 재배포 (INTERNAL_API_KEY 적용)

코드 변경이 없다면 기존 JAR 그대로 재시작. 새 코드가 있다면 로컬에서 빌드 후 전송.

### 3-1. (코드 변경 시) 로컬에서 빌드 & 전송

```bash
cd triagain-back
./gradlew bootJar
scp -i triagain-key.pem build/libs/triagain-0.0.1-SNAPSHOT.jar ec2-user@3.34.132.7:~/
```

### 3-2. EC2에서 재시작

```bash
ssh -i triagain-key.pem ec2-user@3.34.132.7
source ~/env.sh
kill $(pgrep -f triagain)
java -jar triagain-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > app.log 2>&1 &
```

### 3-3. InternalApiKeyFilter 활성화 확인

```bash
tail -f app.log | grep -i "internal"
```

`/internal/**` 경로가 permitAll로 등록되어 있고, `InternalApiKeyFilter`가 API Key 검증을 수행하는 것 확인.

---

## Step 4: AWS SAM CLI 설치 (로컬)

Lambda 배포에 SAM CLI가 필요하다.

### 4-1. 설치

```bash
# macOS
brew install aws-sam-cli

# 설치 확인
sam --version
```

### 4-2. AWS CLI 설정 확인

```bash
aws sts get-caller-identity
# IAM 유저/역할 정보 출력되면 OK
```

> IAM 유저에 필요한 권한: Lambda, CloudFormation, S3, IAM (SAM이 CloudFormation 스택 생성).

---

## Step 5: Lambda 배포

```bash
cd triagain-back/lambda

# 실행 권한 부여
chmod +x deploy-lambda.sh

# 배포 실행
./deploy-lambda.sh http://3.34.132.7:8080 <INTERNAL_API_KEY> triagain-verifications
```

`deploy-lambda.sh`가 자동으로 수행하는 것:
1. `sam build` — Lambda 패키징 (Python 3.12)
2. `sam deploy` — CloudFormation 스택 생성 (`triagain-lambda`)
3. Lambda에 S3 invoke 권한 부여
4. S3 버킷에 PutObject → Lambda 이벤트 알림 설정 (prefix: `upload-sessions/`)

### 배포 후 확인

```bash
# Lambda 함수 존재 확인
aws lambda get-function --function-name triagain-upload-complete --region ap-northeast-2

# S3 이벤트 알림 설정 확인
aws s3api get-bucket-notification-configuration --bucket triagain-verifications --region ap-northeast-2
```

> **주의**: `BackendUrl`은 Lambda가 접근 가능한 주소여야 한다.
> - Lambda는 VPC 밖에서 실행 (SAM 템플릿에 VPC 설정 없음)
> - EC2 Security Group에서 8080 포트가 외부에서 접근 가능해야 함
> - Round 1에서 이미 8080을 열어뒀다면 추가 작업 불필요

---

## Step 6: 네트워크 확인

Lambda → EC2 통신이 가능한지 확인.

### EC2 Security Group 체크

| 항목 | 확인 |
|------|------|
| 인바운드 8080 | Lambda가 VPC 밖이므로 `0.0.0.0/0`에서 8080 허용 (Round 1에서 이미 설정) |

Round 1 배포 시 EC2 포트 `22/80/443/8080`을 열어뒀다면 추가 설정 불필요.

---

## Step 7: E2E 스모크 테스트

전체 플로우를 수동으로 검증한다.

### 7-1. JWT 토큰 준비

```bash
# Round 1에서 만든 test-user-1 사용 (토큰 만료 시 재발급)
TOKEN=$(curl -s -X POST http://3.34.132.7:8080/auth/test-login \
  -H "Content-Type: application/json" \
  -d '{"userId":"test-user-1"}' | jq -r '.accessToken')

echo $TOKEN
```

### 7-2. Upload Session 생성

```bash
curl -s -X POST http://3.34.132.7:8080/upload-sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"test.jpg","contentType":"image/jpeg","fileSize":1024}' | jq .
# → sessionId, presignedUrl 반환
```

반환된 `sessionId`와 `presignedUrl`을 메모.

### 7-3. SSE 구독 (별도 터미널)

```bash
curl -N http://3.34.132.7:8080/upload-sessions/<sessionId>/events
```

> 60초 타임아웃. 업로드가 이 안에 완료되어야 SSE 이벤트를 수신한다.

### 7-4. S3 업로드 (Presigned URL)

```bash
# 테스트 이미지 파일이 없으면 더미 파일 생성
dd if=/dev/urandom of=test.jpg bs=1024 count=1

curl -X PUT "<presignedUrl>" \
  -H "Content-Type: image/jpeg" \
  --data-binary @test.jpg
# → 200 OK
```

### 7-5. SSE 이벤트 수신 확인

SSE 터미널에서 다음이 출력되면 성공:

```
event: upload-complete
data: COMPLETED
```

### 7-6. 인증 생성 (session COMPLETED 상태)

```bash
curl -s -X POST http://3.34.132.7:8080/verifications \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"crewId":"<crewId>","content":"테스트 인증","sessionId":"<sessionId>"}' | jq .
# → 201 Created
```

> `crewId`는 Round 1에서 생성한 크루의 ID. 해당 크루가 PHOTO 인증 타입이어야 sessionId를 사용할 수 있다.

---

## 트러블슈팅

### 체크포인트

| 단계 | 실패 시 확인 |
|------|-------------|
| S3 업로드 | Presigned URL 만료(15분)? Content-Type 일치? |
| Lambda 트리거 | S3 이벤트 알림 설정됨? prefix `upload-sessions/` 맞음? |
| Lambda → Backend | CloudWatch Logs 확인. EC2 SG 8080 열려있음? |
| API Key 검증 | EC2와 Lambda의 `INTERNAL_API_KEY`가 동일한가? |
| SSE 수신 | 60초 타임아웃 전인가? |

### 로그 확인

```bash
# Lambda 로그 (CloudWatch)
aws logs tail /aws/lambda/triagain-upload-complete --follow --region ap-northeast-2

# Backend 로그 (EC2)
ssh -i triagain-key.pem ec2-user@3.34.132.7
tail -f ~/app.log | grep -E "upload|internal|sse"
```

### 자주 발생하는 문제

**Lambda가 트리거되지 않음**
```bash
# S3 이벤트 알림 설정 재확인
aws s3api get-bucket-notification-configuration --bucket triagain-verifications
# LambdaFunctionConfigurations에 triagain-upload-complete가 있는지 확인
```

**Lambda → Backend 연결 실패 (timeout/connection refused)**
```bash
# Lambda에서 EC2로 직접 통신 테스트는 불가하므로, CloudWatch Logs 확인
# "Connection error" → EC2 SG 또는 URL 문제
# "Backend error: 403" → INTERNAL_API_KEY 불일치
```

**SSE 이벤트가 오지 않음**
- 업로드 후 60초 이내인지 확인 (SSE 타임아웃)
- Backend 로그에서 `session COMPLETED` 처리가 되었는지 확인
- SSE 구독 시점이 업로드보다 먼저인지 확인

---

## 참고 파일

| 파일 | 용도 |
|------|------|
| `lambda/handler.py` | Lambda 함수 코드 (S3 이벤트 → Backend API 호출) |
| `lambda/template.yaml` | SAM 템플릿 (Lambda 리소스 정의) |
| `lambda/deploy-lambda.sh` | 원클릭 배포 스크립트 |
| `docs/guide/20260306-first-deploy-guide.md` | Round 1~2 배포 가이드 |
| `docs/prod-deploy-checklist.md` | 운영 체크리스트 |
| `src/.../auth/InternalApiKeyFilter.java` | API Key 검증 필터 |
| `src/.../auth/SecurityConfig.java` | `/internal/**` permitAll 설정 |

---

## 완료 후 정리

배포 완료 후 기존 가이드의 주의사항을 업데이트:

- ~~Lambda는 이번 스코프 밖. `/internal/**`은 prod에서 `denyAll`~~ → Lambda 배포 완료, `/internal/**`은 InternalApiKeyFilter로 보호
