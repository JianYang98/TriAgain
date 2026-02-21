# API 명세 (API Specification)

## 개요

인증 사용자 플로우 (사진 필수 크루인 경우)
→ 텍스트만 인증 가능한 크루라면 바로 POST /verifications

```
1. POST /upload-sessions → presignedUrl 받음
2. S3에 직접 업로드 (PUT {presignedUrl})
3. POST /verifications → 인증 완료
```

---

## 구현 완료

### POST /upload-sessions (이미지 업로드 세션 생성)

클라이언트가 S3에 직접 업로드할 수 있도록 Presigned URL을 발급받는 API

**요청 (Request)**
```json
POST /upload-sessions HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "fileName": "verification_image.jpg",
  "fileType": "image/jpeg",
  "fileSize": 2048576
}
```

**성공 응답 (201 Created)**
```json
{
  "uploadSessionId": "upload_123",
  "presignedUrl": "https://s3.amazonaws.com/bucket/verifications/user_456/2026-02-18/abc123.jpg?X-Amz-Algorithm=...",
  "imageUrl": "https://s3.amazonaws.com/bucket/verifications/user_456/2026-02-18/abc123.jpg",
  "expiresAt": "2026-02-18T15:00:00Z",
  "maxFileSize": 5242880,
  "allowedTypes": ["image/jpeg"]
}
```

**필드 설명:**
- `uploadSessionId`: 업로드 세션 ID (추적용)
- `presignedUrl`: S3에 직접 업로드할 URL (15분 유효)
- `imageUrl`: 업로드 완료 후 사용할 이미지 URL
- `expiresAt`: Presigned URL 만료 시간
- `maxFileSize`: 최대 파일 크기 (5MB)
- `allowedTypes`: 허용된 파일 타입

**실패 응답**
```json
// 400 Bad Request - 파일 타입 불허
{
  "code": "INVALID_FILE_TYPE",
  "message": "지원하지 않는 파일 형식입니다.",
  "allowedTypes": ["image/jpeg", "image/png", "image/webp"]
}

// 400 Bad Request - 파일 크기 초과
{
  "code": "FILE_TOO_LARGE",
  "message": "파일 크기가 너무 큽니다.",
  "maxFileSize": 5242880,
  "requestedSize": 10485760
}

// 401 Unauthorized
{
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다."
}

// 429 Too Many Requests
{
  "code": "UPLOAD_RATE_LIMIT",
  "message": "업로드 요청이 너무 많습니다.",
  "retryAfter": 60
}
```

**제약 사항:**
- 최대 크기: 5MB
- 허용 타입: JPEG, PNG, WebP
- 파일명: UUID 기반 자동 생성
- Presigned URL 유효기간: 15분
- Rate Limit: 사용자당 10건/분
- 미사용 이미지: 업로드 후 7일 경과 시 자동 삭제

---

### POST /verifications (인증 생성)

**요청 (Request)**
```json
POST /verifications HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: <uuid>

{
  "challengeId": "chal_123",
  "imageUrl": "https://s3.../abc123.jpg",
  "uploadSessionId": "upload_123",
  "textContent": "오늘도 달리기 완료!"
}
```

**성공 응답 (201 Created)**
```json
{
  "verificationId": "ver_789",
  "challengeId": "chal_123",
  "userId": "user_456",
  "imageUrl": "https://s3.../image.jpg",
  "status": "APPROVED",
  "reviewStatus": "AUTO_APPROVED",
  "reportCount": 0,
  "targetDate": "2026-02-18",
  "createdAt": "2026-02-18T14:30:00Z"
}
```

**실패 응답**
```json
// 400 Bad Request - 잘못된 요청
{
  "code": "INVALID_REQUEST",
  "message": "필수 입력값이 누락되었습니다.",
  "field": "challengeId"
}

// 400 Bad Request - 유효하지 않은 세션
{
  "code": "INVALID_UPLOAD_SESSION",
  "message": "유효하지 않은 업로드 세션입니다."
}

// 401 Unauthorized
{
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다."
}

// 403 Forbidden
{
  "code": "FORBIDDEN",
  "message": "크루 멤버만 인증할 수 있습니다."
}

// 409 Conflict - 중복 인증
{
  "code": "DUPLICATE_VERIFICATION",
  "message": "이미 해당 날짜에 인증을 완료했습니다.",
  "existingVerificationId": "ver_123"
}

// 422 Unprocessable Entity - 마감 초과
{
  "code": "VERIFICATION_DEADLINE_PASSED",
  "message": "인증 마감 시간이 지났습니다.",
  "deadline": "2026-02-18T23:59:59Z"
}

// 429 Too Many Requests
{
  "code": "TOO_MANY_REQUESTS",
  "message": "잠시 후 다시 시도해주세요.",
  "retryAfter": 3
}
```

**핵심 규칙:**
- S3 업로드 성공 후에만 호출 가능 (upload_session이 PENDING 상태여야 함)
- verification INSERT 성공 후에만 upload_session을 COMPLETED로 전환 (동일 트랜잭션)
- 텍스트 인증 크루인 경우 uploadSessionId, imageUrl 없이 호출 가능
- 마감 시간 기준: upload_session.requested_at (서버 기록, 조작 불가)

---

## TODO (구현 시 추가 예정)

### Crew Context
- POST /crews — 크루 생성
- POST /crews/{crewId}/join — 크루 참여
- GET /crews — 크루 목록 조회
- GET /crews/{crewId} — 크루 상세 조회

### Verification Context
- GET /crews/{crewId}/feed — 크루 피드 조회

### Moderation Context
- POST /verifications/{id}/reports — 신고

### Support Context
- POST /verifications/{id}/reactions — 반응 (이모지)
