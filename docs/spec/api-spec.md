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
// 400 Bad Request - 잘못된 입력값
{
  "code": "INVALID_INPUT",
  "message": "잘못된 입력값입니다.",
  "field": "challengeId"
}

// 400 Bad Request - 사진 인증 필수
{
  "code": "PHOTO_REQUIRED",
  "message": "사진 인증이 필요합니다."
}

// 400 Bad Request - 업로드 세션 미완료
{
  "code": "UPLOAD_SESSION_NOT_COMPLETED",
  "message": "업로드 세션이 완료되지 않았습니다."
}

// 400 Bad Request - 업로드 세션 만료
{
  "code": "UPLOAD_SESSION_EXPIRED",
  "message": "업로드 세션이 만료되었습니다."
}

// 400 Bad Request - 인증 마감 시간 초과
{
  "code": "VERIFICATION_DEADLINE_EXCEEDED",
  "message": "인증 마감 시간이 지났습니다.",
  "deadline": "2026-02-18T23:59:59Z"
}

// 401 Unauthorized
{
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다."
}

// 403 Forbidden - 크루 멤버 아님
{
  "code": "CREW_ACCESS_DENIED",
  "message": "크루 멤버만 조회할 수 있습니다."
}

// 404 Not Found - 업로드 세션 없음
{
  "code": "UPLOAD_SESSION_NOT_FOUND",
  "message": "업로드 세션을 찾을 수 없습니다."
}

// 409 Conflict - 중복 인증
{
  "code": "VERIFICATION_ALREADY_EXISTS",
  "message": "이미 해당 날짜에 인증이 존재합니다.",
  "existingVerificationId": "ver_123"
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

---

### POST /auth/kakao (카카오 로그인)

카카오 Access Token으로 기존 유저 여부를 확인한다.
- **기존 유저** → JWT 발급 (로그인 완료)
- **신규 유저** → `isNewUser=true` + 카카오 프로필 반환 (JWT 미발급, 유저 미생성)

**요청 (Request)**
```
POST /auth/kakao HTTP/1.1
Content-Type: application/json
```
```json
{
  "kakaoAccessToken": "카카오_SDK에서_받은_access_token"
}
```

**시나리오 1: 기존 유저 로그인 성공 (200 OK)**
```json
{
  "status": "OK",
  "data": {
    "isNewUser": false,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "id": "1234567890",
      "nickname": "김철수",
      "profileImageUrl": "https://img.kakao.com/profile.jpg"
    },
    "kakaoId": null,
    "kakaoProfile": null
  }
}
```

**시나리오 2: 신규 유저 — 회원가입 필요 (200 OK)**
```json
{
  "status": "OK",
  "data": {
    "isNewUser": true,
    "accessToken": null,
    "refreshToken": null,
    "accessTokenExpiresIn": null,
    "user": null,
    "kakaoId": "1234567890",
    "kakaoProfile": {
      "nickname": "카카오닉네임",
      "email": "user@kakao.com",
      "profileImageUrl": "https://img.kakao.com/profile.jpg"
    }
  }
}
```

**프론트 분기 로직:**
```
1. POST /auth/kakao 호출
2. if (data.isNewUser == false):
     → accessToken/refreshToken 저장 → 메인 화면 이동
3. if (data.isNewUser == true):
     → data.kakaoId, data.kakaoProfile 저장
     → 약관 동의 + 닉네임 입력 화면 이동
     → POST /auth/signup 호출
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A001 | 유효하지 않은 카카오 토큰입니다. |
| 502 | A002 | 카카오 API 호출 중 오류가 발생했습니다. |

---

### POST /auth/signup (회원가입)

카카오 인증 + 약관 동의 + 닉네임으로 신규 유저를 생성하고 JWT를 발급한다.

**요청 (Request)**
```
POST /auth/signup HTTP/1.1
Content-Type: application/json
```
```json
{
  "kakaoAccessToken": "카카오_SDK에서_받은_access_token",
  "kakaoId": "1234567890",
  "nickname": "내닉네임",
  "termsAgreed": true
}
```

**필드 설명:**
- `kakaoAccessToken`: (필수) 카카오 SDK에서 받은 Access Token
- `kakaoId`: (필수) POST /auth/kakao 응답의 `kakaoId` 값
- `nickname`: (필수) 2~12자, 한글/영문/숫자/언더스코어만 허용
- `termsAgreed`: (필수) 약관 동의 여부 (true만 허용)

**성공 응답 (201 Created)**
```json
{
  "status": "OK",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "id": "1234567890",
      "nickname": "내닉네임",
      "profileImageUrl": "https://img.kakao.com/profile.jpg"
    }
  }
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | U005 | 약관에 동의해야 회원가입이 가능합니다. | termsAgreed=false |
| 400 | U004 | 닉네임은 필수입니다. | 빈값/null |
| 400 | U007 | 닉네임은 2~12자의 한글, 영문, 숫자, 언더스코어만 사용할 수 있습니다. | 형식 불일치 |
| 400 | U008 | 카카오 계정 정보가 일치하지 않습니다. | kakaoId 불일치 |
| 401 | A001 | 유효하지 않은 카카오 토큰입니다. | 만료/잘못된 토큰 |
| 409 | U006 | 이미 가입된 사용자입니다. | 중복 가입 |

---

### POST /auth/refresh (토큰 갱신)

Refresh Token으로 새 Access Token을 발급한다.

**요청 (Request)**
```
POST /auth/refresh HTTP/1.1
Content-Type: application/json
```
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**성공 응답 (200 OK)**
```json
{
  "status": "OK",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800
  }
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A004 | 유효하지 않은 리프레시 토큰입니다. |
| 404 | U001 | 사용자를 찾을 수 없습니다. |

---

## TODO (구현 시 추가 예정)

### Crew Context
- POST /crews — 크루 생성 (deadlineTime: 선택, 기본값 23:59:59)
- POST /crews/{crewId}/join — 크루 참여
- GET /crews — 크루 목록 조회
- GET /crews/{crewId} — 크루 상세 조회 (응답에 deadlineTime, 멤버별 nickname/profileImageUrl 포함)

### Verification Context
- GET /crews/{crewId}/feed — 크루 피드 조회

### Moderation Context
- POST /verifications/{id}/reports — 신고

### Support Context
- POST /verifications/{id}/reactions — 반응 (이모지)
