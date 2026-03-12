# PR Review: feat/s3-lambda-presigned-url

> 리뷰 일자: 2026-03-11
> 브랜치: `feat/s3-lambda-presigned-url`
> 커밋: 3건 (380fc1c → 6f76012 → 4335002)

---

## 변경 요약

Lambda + Internal API Key 기반 업로드 세션 완료 처리.
S3 PutObject 이벤트 → Python Lambda → `PUT /internal/upload-sessions/complete?imageKey={key}` → DB PENDING→COMPLETED + SSE 전송.

---

## Issues

### 1. [CRITICAL] `image_key` 컬럼에 인덱스 없음

- **파일**: `src/main/resources/db/migration/V1__initial_schema.sql:67`
- **조회 코드**: `UploadSessionJpaRepository.java:14` — `findByImageKey()`
- **문제**: Lambda 콜백마다 `image_key`로 조회하는데 인덱스가 없어서 full table scan
- **수정**: Flyway 마이그레이션 추가

```sql
-- V7__add_index_upload_session_image_key.sql
CREATE INDEX idx_upload_session_image_key ON upload_session (image_key);
```

---

### 2. [MEDIUM] API Key 비교 타이밍 공격 취약

- **파일**: `src/main/java/com/triagain/common/auth/InternalApiKeyFilter.java:28`
- **문제**: `expectedApiKey.equals(requestApiKey)` — constant-time 비교가 아님. 문자열 앞부분부터 비교하므로 타이밍 차이로 키 추론 가능
- **수정**:

```java
// 변경 전
if (!expectedApiKey.equals(requestApiKey)) {

// 변경 후
if (requestApiKey == null || !MessageDigest.isEqual(
        expectedApiKey.getBytes(), requestApiKey.getBytes())) {
```

- `import java.security.MessageDigest;` 추가 필요

---

### 3. [MEDIUM] SAM 템플릿 — 기존 S3 버킷과 충돌

- **파일**: `lambda/template.yaml:44-56`
- **문제**: `VerificationBucket` 리소스가 `triagain-verifications` 버킷을 CREATE함. 이 버킷은 이미 수동 생성되어 존재. CloudFormation 배포 시 `BucketAlreadyExists` 에러 발생
- **수정 방향**: 기존 버킷을 참조하도록 변경. `VerificationBucket` 리소스 제거하고 Lambda 이벤트 트리거를 기존 버킷 ARN으로 연결

---

### 4. [LOW] `deploy.sh` newline 누락 + IP 하드코딩

- **파일**: `deploy.sh`
- **문제**: 파일 끝 newline 없음. EC2 IP `3.34.132.7` 하드코딩
- **수정**: newline 추가. 개인 배포 스크립트이므로 `.gitignore` 추가 고려

---

### 5. [LOW] dev/local 환경 `/internal/**` API Key 미검증

- **파일**: `DevSecurityConfig.java`
- **문제**: `InternalApiKeyFilter`가 prod profile에만 등록됨. dev/local에서는 인증 없이 `/internal/**` 접근 가능
- **판단**: 개발 편의성 관점에서 의도된 설계. dev 환경 외부 노출 시 주의

---

### 6. [LOW] 멱등 처리 시 불필요한 save()

- **파일**: `CompleteUploadSessionService.java:29-30`
- **문제**: 이미 COMPLETED인 세션도 `complete()` → `save()` 호출. 상태 변경 없이 DB write 발생
- **판단**: 도메인 모델에 변경 추적이 없으므로 현재는 그대로 두는 게 단순. Phase 1에서 무시 가능

---

## 긍정적인 점

- **imageKey 기반 조회**: Lambda는 S3 key만 알고 session ID를 모름. imageKey로 조회하는 설계가 적절
- **afterCommit SSE**: 트랜잭션 커밋 후 SSE 전송으로 데이터 일관성 보장
- **멱등성**: `complete()`가 COMPLETED/USED 상태면 무시 → Lambda 재시도에 안전
- **Python Lambda**: 단순 HTTP 호출만 하므로 cold start가 빠른 Python 선택 적절
- **테스트**: BDD 스타일 4케이스 (성공, NOT_FOUND, 멱등, EXPIRED). `TransactionSynchronization` 수동 테스트까지 커버
- **문서 동기화**: api-spec.md, photo-upload-lambda-sse.md, sequence/verification.md 모두 코드와 일치

---

## 수정 우선순위

| 순위 | 항목 | 난이도 |
|------|------|--------|
| 1 | `image_key` 인덱스 추가 | 5분 |
| 2 | API Key 타이밍 공격 방어 | 5분 |
| 3 | SAM 템플릿 기존 버킷 충돌 해결 | 15분 |
| 4 | `deploy.sh` newline + gitignore | 2분 |
