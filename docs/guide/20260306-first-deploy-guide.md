# 첫 AWS 배포 가이드 (2026-03-06)

> 브랜치: `feat/s3-lambda-presigned-url`
> 목표: Spring Boot 앱 EC2 배포 + S3 Presigned URL 실제 동작 검증

---

## AWS 리소스

| 리소스 | 값 |
|--------|-----|
| EC2 | `3.34.132.7` (t3.micro, 서울, 포트 22/80/443/8080) |
| RDS | `triagain-db.cxis2q4z2sto.ap-northeast-2.rds.amazonaws.com` |
| S3 | `triagain-verifications` (서울, 퍼블릭 차단, CORS 완료) |
| 키페어 | `triagain-key.pem` |

---

## Step 1: EC2 환경 세팅

### 1-1. Java 17 설치
```bash
ssh -i triagain-key.pem ec2-user@3.34.132.7
sudo yum install java-17-amazon-corretto-headless -y
java -version
```

### 1-2. EC2 IAM Role에 S3 권한 추가
AWS 콘솔 → EC2 → triagain-server → IAM 역할 수정

최소 권한 정책:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject"],
    "Resource": "arn:aws:s3:::triagain-verifications/*"
  }]
}
```

> S3Config의 `S3Presigner.builder()`가 credential 미명시 → EC2 IAM Role 자동 사용. Access Key 불필요.

### 1-3. 환경변수 설정
EC2에서 `~/env.sh` 생성:
```bash
cat > ~/env.sh << 'EOF'
export DB_URL=jdbc:postgresql://triagain-db.cxis2q4z2sto.ap-northeast-2.rds.amazonaws.com:5432/triagain
export DB_USERNAME=<RDS 사용자명>
export DB_PASSWORD=<RDS 비밀번호>
export JWT_SECRET=<Base64 인코딩된 256bit 이상 키>
EOF
```

---

## Step 2: 빌드 & 배포

### 2-1. 로컬에서 JAR 전송
```bash
cd triagain-back
./gradlew bootJar
scp -i triagain-key.pem build/libs/triagain-0.0.1-SNAPSHOT.jar ec2-user@3.34.132.7:~/
```

### 2-2. Round 1 — dev 프로필로 시작 (기본 API 검증)
```bash
ssh -i triagain-key.pem ec2-user@3.34.132.7
source ~/env.sh
java -jar triagain-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev > app.log 2>&1 &
tail -f app.log
```

---

## Step 3: 검증

### Round 1 — dev 프로필 (기본 API 검증)

#### 3-1. Flyway 마이그레이션 확인
- [ ] `app.log`에서 `Successfully applied 6 migrations` 확인

#### 3-2. Health Check
```bash
curl http://3.34.132.7:8080/health
# 기대: 200 OK
```

#### 3-3. 테스트 유저 생성 + JWT 발급
```bash
# psql 설치
sudo yum install postgresql16 -y

# RDS 접속
psql -h triagain-db.cxis2q4z2sto.ap-northeast-2.rds.amazonaws.com -U <username> -d triagain

# 테스트 유저 INSERT
INSERT INTO users (id, nickname, provider, created_at)
VALUES ('test-user-1', '테스트유저', 'TEST', NOW());
 

# test-login으로 JWT 발급
curl -X POST http://3.34.132.7:8080/auth/test-login \
  -H "Content-Type: application/json" \
  -d '{"userId":"test-user-1"}'
# 기대: accessToken, refreshToken 반환
```

#### 3-4. 기본 API 검증
```bash
TOKEN=<accessToken>

# 크루 생성
curl -X POST http://3.34.132.7:8080/crews \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"테스트크루","goal":"테스트","verificationType":"TEXT","maxMembers":5,"startDate":"2026-03-10","endDate":"2026-03-20"}'

# 내 크루 목록
curl http://3.34.132.7:8080/crews \
  -H "Authorization: Bearer $TOKEN"
```

### Round 2 — prod 프로필로 전환 (S3 Presigned URL 검증)

dev 검증 완료 후 prod로 전환. dev에서 발급한 JWT는 같은 `JWT_SECRET`이므로 그대로 사용.

```bash
kill $(pgrep -f triagain)
java -jar triagain-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > app.log 2>&1 &
tail -f app.log
```

#### 3-5. S3 Presigned URL 검증
```bash
# Upload Session 생성
curl -X POST http://3.34.132.7:8080/upload-sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"test.jpg","contentType":"image/jpeg","fileSize":1024}'
# 기대: presignedUrl = https://triagain-verifications.s3.ap-northeast-2.amazonaws.com/...

# Presigned URL로 실제 S3 업로드
curl -X PUT "<presigned-url>" \
  -H "Content-Type: image/jpeg" \
  --data-binary @test.jpg
# 기대: 200 OK → AWS 콘솔에서 S3 파일 확인
```

---

## 주의사항

- Lambda는 이번 스코프 밖. `/internal/**`은 prod에서 `denyAll`
- HTTPS 미설정 — Phase 1은 HTTP(8080) 직접 접근
- `env.sh`는 git에 올리지 않음. EC2에만 존재
- dev/prod 모두 같은 `JWT_SECRET` 사용 시 토큰 호환
