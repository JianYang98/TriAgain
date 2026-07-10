# API 명세 (API Specification)

## 개요

인증 사용자 플로우 (사진 필수 크루인 경우)
→ 텍스트만 인증 가능한 크루라면 바로 POST /verifications

```
1. POST /upload-sessions → presignedUrl + sessionId 수신
2. GET /upload-sessions/{id}/events (SSE 구독)
3. S3에 직접 업로드 (PUT {presignedUrl})
4. Lambda → 자동 완료 감지 → SSE "COMPLETED" 수신
5. POST /verifications → 인증 완료
```

---

## 구현 완료

### POST /upload-sessions (이미지 업로드 세션 생성)

클라이언트가 S3에 직접 업로드할 수 있도록 Presigned URL을 발급받는 API

**`crewId` / `habitId` 중 정확히 하나 필수 (XOR)** — 크루 인증용 세션은 `crewId`로, 솔로(습관) 인증용 세션은 `habitId`로 발급한다. 세션은 발급 컨텍스트에 구속되며, 인증 생성 시 해당 컨텍스트(`crewId` 또는 `habitId`)와 대조한다 (크루는 기존 `UPLOAD_SESSION_CREW_MISMATCH`, 솔로는 `HABIT_UPLOAD_SESSION_MISMATCH`). 하위 호환: 기존 크루 호출부는 계속 `crewId`만 전송하며 거동 불변.

**요청 (Request) — 크루 인증용**
```json
POST /upload-sessions HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "crewId": "crew_123",
  "fileName": "verification_image.jpg",
  "fileType": "image/jpeg",
  "fileSize": 2048576
}
```

**요청 (Request) — 솔로(습관) 인증용**
```json
POST /upload-sessions HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "habitId": "HBIT-a1b2c3d4e5f60708",
  "fileName": "verification_image.jpg",
  "fileType": "image/jpeg",
  "fileSize": 2048576
}
```

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "uploadSessionId": 123,
    "presignedUrl": "https://s3.amazonaws.com/bucket/verifications/user_456/2026-02-18/abc123.jpg?X-Amz-Algorithm=...",
    "imageUrl": "https://s3.amazonaws.com/bucket/verifications/user_456/2026-02-18/abc123.jpg",
    "expiresAt": "2026-02-18T15:00:00Z",
    "maxFileSize": 5242880,
    "allowedTypes": ["image/jpeg"]
  },
  "error": null
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

// 400 Bad Request - TEXT 크루에서 upload session 생성 시도
{
  "code": "UPLOAD_SESSION_NOT_REQUIRED",
  "message": "텍스트 인증 크루에서는 업로드 세션이 필요하지 않습니다."
}

// 400 Bad Request - 크루가 ACTIVE 상태가 아님
{
  "code": "CREW_NOT_ACTIVE",
  "message": "활성 상태의 크루가 아닙니다."
}

// 400 Bad Request - 크루 시작 전
{
  "code": "CREW_NOT_STARTED",
  "message": "크루가 아직 시작되지 않았습니다."
}

// 400 Bad Request - 크루 기간 종료
{
  "code": "CREW_PERIOD_ENDED",
  "message": "크루 기간이 종료되었습니다."
}

// 400 Bad Request - 인증 마감 시간 초과
{
  "code": "VERIFICATION_DEADLINE_EXCEEDED",
  "message": "인증 마감 시간이 지났습니다."
}

// 403 Forbidden - 크루 멤버 아님
{
  "code": "CREW_ACCESS_DENIED",
  "message": "크루 멤버만 조회할 수 있습니다."
}

// 404 Not Found - 습관을 찾을 수 없음 (솔로 세션)
{
  "code": "HABIT_NOT_FOUND",
  "message": "습관을 찾을 수 없습니다."
}

// 403 Forbidden - 본인 습관이 아님 (솔로 세션)
{
  "code": "HABIT_ACCESS_DENIED",
  "message": "본인 습관만 이용할 수 있습니다."
}

// 400 Bad Request - 멈춘 습관 (솔로 세션)
{
  "code": "HABIT_NOT_ACTIVE",
  "message": "멈춘 습관입니다. 재개 후 이용할 수 있습니다."
}

// 400 Bad Request - crewId/habitId 둘 다 없거나 둘 다 존재 (XOR 위반)
{
  "code": "INVALID_INPUT",
  "message": "잘못된 입력값입니다."
}

```

**제약 사항:**
- 최대 크기: 5MB
- 허용 타입: JPEG, PNG, WebP
- 파일명: UUID 기반 자동 생성
- Presigned URL 유효기간: 15분
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
  "crewId": "crew_123",
  "challengeId": "chal_123",
  "uploadSessionId": 123,
  "textContent": "오늘도 달리기 완료!"
}
```

**필드 설명:**
- `challengeId`: (조건부) 챌린지 ID — `crewId`와 둘 중 하나 이상 필수
- `crewId`: (조건부) 크루 ID — `challengeId`와 둘 중 하나 이상 필수
- `uploadSessionId`: (선택) 업로드 세션 ID — 사진 인증 크루에서만 필요
- `textContent`: (선택) 인증 텍스트

**challengeId / crewId 조합 규칙:**
| challengeId | crewId | 동작 |
|:-----------:|:------:|------|
| O | O | 챌린지 조회 후 crewId 일치 검증 (불일치 시 CHALLENGE_CREW_MISMATCH) |
| O | X | challengeId로 챌린지 조회, crewId는 챌린지에서 추출 |
| X | O | crewId로 활성 챌린지 조회 또는 자동 생성 |
| X | X | 400 Bad Request |

```json
// challengeId 생략 예시 (새 챌린지 자동 생성)
{
  "crewId": "crew_123",
  "textContent": "오늘도 달리기 완료!"
}
```

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "verificationId": "ver_789",
    "challengeId": "chal_123",
    "userId": "user_456",
    "crewId": "crew_123",
    "imageUrl": "https://s3.../image.jpg",
    "textContent": "오늘도 달리기 완료!",
    "status": "APPROVED",
    "reviewStatus": "NOT_REQUIRED",
    "reportCount": 0,
    "targetDate": "2026-02-18",
    "createdAt": "2026-02-18T14:30:00Z"
  },
  "error": null
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

// 400 Bad Request - upload session의 crewId와 요청 crewId 불일치
{
  "code": "UPLOAD_SESSION_CREW_MISMATCH",
  "message": "업로드 세션의 크루 정보가 일치하지 않습니다."
}

// 409 Conflict - 중복 인증
{
  "code": "VERIFICATION_ALREADY_EXISTS",
  "message": "이미 해당 날짜에 인증이 존재합니다.",
  "existingVerificationId": "ver_123"
}

```

**핵심 규칙:**
- upload_session이 COMPLETED 상태여야 함 (Lambda가 S3 업로드 완료 감지 후 COMPLETED 전환)
- verification 생성 시 session COMPLETED 확인만 수행, 중복 사용은 DB UNIQUE constraint(verification.upload_session_id)로 방지
- 텍스트 인증 크루인 경우 uploadSessionId, imageUrl 없이 호출 가능
- 마감 시간 기준: upload_session.requested_at (서버 기록, 조작 불가)

---

---

### POST /auth/kakao (카카오 로그인)

카카오 Access Token으로 기존 유저 여부를 확인한다.
- **기존 유저** → JWT 발급 (로그인 완료). **email·profileImageUrl 모두 동기화하지 않음** (최초 가입 시에만 저장, 이후 유저가 직접 관리)
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
  "success": true,
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
  },
  "error": null
}
```

**시나리오 2: 신규 유저 — 회원가입 필요 (200 OK)**
```json
{
  "success": true,
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
  },
  "error": null
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
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "id": "1234567890",
      "nickname": "내닉네임",
      "profileImageUrl": "https://img.kakao.com/profile.jpg"
    }
  },
  "error": null
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

### POST /auth/apple (Apple 로그인)

Apple Identity Token으로 기존 유저 여부를 확인한다.
- **기존 유저** → JWT 발급 (로그인 완료)
- **신규 유저** → `isNewUser=true` + appleId/email 반환 (JWT 미발급, 유저 미생성)

**요청 (Request)**
```
POST /auth/apple HTTP/1.1
Content-Type: application/json
```
```json
{
  "identityToken": "Apple_SDK에서_받은_identity_token",
  "authorizationCode": "Apple_SDK에서_받은_authorization_code"
}
```

**필드 설명:**
- `identityToken`: (필수) Apple SDK에서 받은 Identity Token (JWT)
- `authorizationCode`: (옵셔널) Apple SDK에서 받은 authorization code. 기존 사용자가 함께 보내면 서버가 Apple `/auth/token`과 교환하여 refresh_token을 backfill 저장 (회원탈퇴 시 revoke에 사용). 누락해도 로그인은 정상 진행. **가능하면 항상 전송 권장**.

**시나리오 1: 기존 유저 로그인 성공 (200 OK)**
```json
{
  "success": true,
  "data": {
    "isNewUser": false,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "id": "001234.abcdef.5678",
      "nickname": "유저닉네임",
      "profileImageUrl": null
    },
    "appleId": null,
    "email": null
  },
  "error": null
}
```

**시나리오 2: 신규 유저 — 회원가입 필요 (200 OK)**
```json
{
  "success": true,
  "data": {
    "isNewUser": true,
    "accessToken": null,
    "refreshToken": null,
    "accessTokenExpiresIn": null,
    "user": null,
    "appleId": "001234.abcdef.5678",
    "email": "user@privaterelay.appleid.com"
  },
  "error": null
}
```

**프론트 분기 로직:**
```
1. POST /auth/apple 호출
2. if (data.isNewUser == false):
     → accessToken/refreshToken 저장 → 메인 화면 이동
3. if (data.isNewUser == true):
     → data.appleId 저장
     → 약관 동의 + 닉네임 입력 화면 이동
     → POST /auth/apple-signup 호출
```

**참고:**
- Apple은 email을 최초 로그인 시에만 제공. 재로그인 시 email은 null일 수 있음
- Apple은 프로필 이미지를 제공하지 않음 (profileImageUrl은 항상 null)
- `authorizationCode` backfill: 기존 사용자가 보내면 서버가 Apple `/auth/token` 교환으로 refresh_token 발급·저장. 교환 실패해도 로그인 자체는 성공 처리(WARN 로그). **신규 유저 응답 분기에서는 backfill을 시도하지 않는다** (회원가입 시점에 처리).

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A005 | 유효하지 않은 애플 토큰입니다. |
| 502 | A006 | 애플 토큰 검증 중 오류가 발생했습니다. |

---

### POST /auth/apple-signup (Apple 회원가입)

Apple 인증 + 약관 동의 + 닉네임으로 신규 유저를 생성하고 JWT를 발급한다.

**요청 (Request)**
```
POST /auth/apple-signup HTTP/1.1
Content-Type: application/json
```
```json
{
  "identityToken": "Apple_SDK에서_받은_identity_token",
  "appleId": "001234.abcdef.5678",
  "nickname": "내닉네임",
  "termsAgreed": true,
  "authorizationCode": "Apple_SDK에서_받은_authorization_code"
}
```

**필드 설명:**
- `identityToken`: (필수) Apple SDK에서 받은 Identity Token (JWT)
- `appleId`: (필수) POST /auth/apple 응답의 `appleId` 값
- `nickname`: (필수) 2~12자, 한글/영문/숫자/언더스코어만 허용
- `termsAgreed`: (필수) 약관 동의 여부 (true만 허용)
- `authorizationCode`: **(필수)** Apple SDK에서 받은 authorization code. 서버가 Apple `/auth/token`과 교환하여 refresh_token을 발급받아 저장한다. 회원탈퇴 시 Apple `/auth/revoke` 호출에 사용 (App Store 5.1.1(v) 요건). 1회용이므로 로그인 화면에서 받은 값을 회원가입 화면까지 state로 전달해야 한다.

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "id": "001234.abcdef.5678",
      "nickname": "내닉네임",
      "profileImageUrl": null
    }
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | U005 | 약관에 동의해야 회원가입이 가능합니다. | termsAgreed=false |
| 400 | U004 | 닉네임은 필수입니다. | 빈값/null |
| 400 | U007 | 닉네임은 2~12자의 한글, 영문, 숫자, 언더스코어만 사용할 수 있습니다. | 형식 불일치 |
| 400 | U009 | 애플 계정 정보가 일치하지 않습니다. | appleId 불일치 |
| 401 | A005 | 유효하지 않은 애플 토큰입니다. | 만료/잘못된 토큰 |
| 409 | U006 | 이미 가입된 사용자입니다. | 중복 가입 |
| 502 | A007 | 애플 인증 코드 교환 중 오류가 발생했습니다. | authorizationCode → refresh_token 교환 실패. 회원가입 차단 |

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
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 1800
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A004 | 유효하지 않은 리프레시 토큰입니다. |
| 404 | U001 | 사용자를 찾을 수 없습니다. |

---

### POST /auth/logout (로그아웃)

Phase 1에서는 서버 no-op. 클라이언트가 로컬 토큰을 삭제하여 로그아웃 처리한다.
Phase 2에서 Redis 기반 토큰 블랙리스트 도입 예정.

**요청 (Request)**
```
POST /auth/logout HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**프론트 처리:**
1. `POST /auth/logout` 호출
2. 로컬 저장소에서 accessToken, refreshToken 삭제
3. 로그인 화면으로 이동

---

### GET /users/me (내 프로필 조회)

인증된 사용자의 프로필 정보를 조회한다.

**요청 (Request)**
```
GET /users/me HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "1234567890",
    "nickname": "내닉네임",
    "profileImageUrl": "https://img.kakao.com/profile.jpg",
    "email": "user@kakao.com"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### PATCH /users/me/nickname (닉네임 변경)

닉네임을 변경하고 변경된 전체 프로필을 반환한다.

**요청 (Request)**
```
PATCH /users/me/nickname HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "nickname": "새닉네임"
}
```

**필드 설명:**
- `nickname`: (필수) 2~12자, 한글/영문/숫자/언더스코어만 허용

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "1234567890",
    "nickname": "새닉네임",
    "profileImageUrl": "https://img.kakao.com/profile.jpg",
    "email": "user@kakao.com"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 400 | U007 | 닉네임은 2~12자의 한글, 영문, 숫자, 언더스코어만 사용할 수 있습니다. |
| 401 | A003 | 인증이 필요합니다. |

---

### POST /users/me/profile-image/upload-session (프로필 이미지 업로드 세션)

프로필 이미지를 S3에 직접 업로드할 수 있도록 Presigned URL을 발급한다.

**요청 (Request)**
```
POST /users/me/profile-image/upload-session HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "fileName": "profile.jpg",
  "fileType": "image/jpeg",
  "fileSize": 512000
}
```

**필드 설명:**
- `fileName`: (필수) 파일명
- `fileType`: (필수) MIME 타입 — image/jpeg, image/png, image/webp만 허용
- `fileSize`: (필수) 파일 크기 (바이트) — 최대 5MB

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "presignedUrl": "https://s3.amazonaws.com/bucket/profiles/user_456/abc123.jpg?X-Amz-Algorithm=...",
    "imageUrl": "https://s3.amazonaws.com/bucket/profiles/user_456/abc123.jpg",
    "expiresAt": "2026-04-14T15:00:00"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 400 | V007 | 지원하지 않는 파일 형식입니다. |
| 400 | V008 | 파일 크기가 너무 큽니다. |
| 401 | A003 | 인증이 필요합니다. |

**제약 사항:**
- 최대 크기: 5MB
- 허용 타입: JPEG, PNG, WebP
- Presigned URL 유효기간: 15분
- S3 경로: profiles/{userId}/{uuid}.{ext}

---

### PATCH /users/me/profile-image (프로필 이미지 변경 확정)

S3 업로드 완료 후 프로필 이미지 URL을 DB에 반영한다.

**요청 (Request)**
```
PATCH /users/me/profile-image HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "imageUrl": "https://s3.amazonaws.com/bucket/profiles/user_456/abc123.jpg"
}
```

**필드 설명:**
- `imageUrl`: (nullable) 이미지 URL — null이면 기본 이미지로 리셋, 값이 있으면 S3 버킷 + profiles/{userId}/ 경로 검증

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "user_456",
    "nickname": "지안",
    "profileImageUrl": "https://s3.amazonaws.com/bucket/profiles/user_456/abc123.jpg",
    "email": "user@kakao.com"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 400 | U011 | 유효하지 않은 이미지 URL입니다. |
| 401 | A003 | 인증이 필요합니다. |

**검증 규칙:**
- imageUrl이 null이면 기본 이미지로 리셋 (profileImageUrl = null → FE에서 기본 아바타 표시)
- imageUrl이 값이 있으면: 우리 S3 버킷 경로인지 확인 + 해당 유저의 profiles/{userId}/ 경로인지 확인

---

### DELETE /users/me (회원탈퇴)

회원을 탈퇴 처리한다. 개인정보를 초기화하고 기존 토큰을 무효화한다.

**인증**: Bearer Token 필수

**요청 (Request)**
```
DELETE /users/me HTTP/1.1
Authorization: Bearer <token>
```

**처리 흐름:**
1. 검증: USER_NOT_FOUND, USER_WITHDRAWN
2. **Apple 연결 해제** (provider=APPLE && apple_refresh_token != null): Apple `/auth/revoke` 호출 (트랜잭션 밖, 실패해도 graceful 진행)
3. 트랜잭션: 크루 정리(LEADER+혼자 → 하드 삭제 / LEADER+멤버≥1 → 가장 오래된 멤버에게 자동 위임 후 본인 제거 / MEMBER → 제거) → 개인정보 초기화 → tokenVersion++ → apple_refresh_token=null

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**참고:**
- Apple 사용자 탈퇴 시 Apple `/auth/revoke` 호출 결과는 응답에 영향을 주지 않는다 (성공/실패 모두 200 OK)
- App Store Review Guideline 5.1.1(v) 요건: Sign in with Apple 사용자는 탈퇴 시 Apple 연결 해제 호출이 필수
- backfill 안 된 기존 Apple 사용자(`apple_refresh_token == null`)는 Apple 측 연결이 그대로 남는다 — 다음 로그인 backfill 후 재탈퇴하거나, 사용자가 직접 Apple ID 설정에서 해제 필요

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 403 | U010 | 탈퇴한 사용자입니다. | deleted_at이 이미 설정됨 |

---

### GET /crews/{crewId}/feed (크루 피드 조회)

크루원들의 인증 목록과 나의 챌린지 현황을 조회한다.

**요청 (Request)**
```
GET /crews/{crewId}/feed?page=0&size=20 HTTP/1.1
Authorization: Bearer <token>
```

**쿼리 파라미터:**
- `page`: (선택) 페이지 번호 (기본값 0)
- `size`: (선택) 페이지 크기 (기본값 20, 최대 50)

**성공 응답 (200 OK) — 활성 챌린지 있는 경우**
```json
{
  "success": true,
  "data": {
    "verifications": [
      {
        "id": "ver_789",
        "userId": "user_456",
        "nickname": "김철수",
        "profileImageUrl": "https://img.kakao.com/profile.jpg",
        "imageUrl": "https://s3.../image.jpg",
        "textContent": "오늘도 달리기 완료!",
        "targetDate": "2026-03-04",
        "createdAt": "2026-03-04T14:30:00"
      }
    ],
    "myProgress": {
      "challengeId": "chal_123",
      "status": "IN_PROGRESS",
      "completedDays": 1,
      "targetDays": 3
    },
    "hasNext": false
  },
  "error": null
}
```

**성공 응답 (200 OK) — 활성 챌린지 없는 경우 (myProgress: null)**
```json
{
  "success": true,
  "data": {
    "verifications": [],
    "myProgress": null,
    "hasNext": false
  },
  "error": null
}
```

**필드 설명:**
- `verifications`: 크루 인증 목록 (최신순 정렬)
  - `id`: 인증 ID
  - `userId`: 작성자 ID
  - `nickname`: 작성자 닉네임
  - `profileImageUrl`: 작성자 프로필 이미지 (nullable)
  - `imageUrl`: 인증 이미지 URL (nullable — 텍스트 인증 크루)
  - `textContent`: 인증 텍스트 (nullable — 사진 인증 크루에서 텍스트 미입력 시)
  - `targetDate`: 인증 대상 날짜
  - `createdAt`: 인증 생성 시각
- `myProgress`: 나의 챌린지 현황 (**nullable** — 활성 챌린지가 없으면 null)
  - `challengeId`: 챌린지 ID
  - `status`: 챌린지 상태 (IN_PROGRESS, SUCCESS, FAILED, ENDED)
  - `completedDays`: 완료한 일수
  - `targetDays`: 목표 일수 (3)
- `hasNext`: 다음 페이지 존재 여부

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |
| 403 | CREW_ACCESS_DENIED | 크루 멤버만 조회할 수 있습니다. | 크루 미참여 |
| 404 | CREW_NOT_FOUND | 존재하지 않는 크루입니다. | 크루 없음 |

---

### GET /crews/{crewId}/my-verifications (내 인증 현황 조회)

크루 내 내 인증 날짜, 연속 스트릭, 작심삼일 달성 횟수를 조회한다.

**요청 (Request)**
```
GET /crews/{crewId}/my-verifications HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "verifiedDates": ["2026-03-01", "2026-03-02", "2026-03-03"],
    "streakCount": 3,
    "completedChallenges": 2,
    "myProgress": {
      "challengeId": "chg_abc123",
      "status": "IN_PROGRESS",
      "completedDays": 2,
      "targetDays": 3
    }
  },
  "error": null
}
```

**필드 설명:**
- `verifiedDates`: APPROVED 인증 날짜 목록 (크루 기간 범위 내, ASC 정렬)
- `streakCount`: 최근 날짜부터 역방향 연속 인증 일수
- `completedChallenges`: challenges.status = SUCCESS 개수 (작심삼일 달성 횟수)
- `myProgress`: 나의 현재 챌린지 현황 (**nullable** — 활성 챌린지가 없으면 null)
  - `challengeId`: 챌린지 ID
  - `status`: 챌린지 상태 (IN_PROGRESS, SUCCESS, FAILED, ENDED)
  - `completedDays`: 완료한 일수
  - `targetDays`: 목표 일수 (3)

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 크루 미참여 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 크루 없음 |

---

### GET /crews/invite/{inviteCode} (초대코드로 크루 미리보기)

초대코드로 크루 정보를 미리 조회한다. 가입하지 않고 조회만 수행하며, 가입 가능 여부(joinable)와 차단 사유(joinBlockedReason)를 함께 반환한다.

**요청 (Request)**
```
GET /crews/invite/ABC123 HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "crew_123",
    "creatorId": "user_001",
    "name": "작심삼일 크루",
    "goal": "매일 운동하기",
    "verificationContent": "운동 완료 인증샷 찍기",
    "verificationType": "PHOTO",
    "maxMembers": 10,
    "currentMembers": 3,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "deadlineTime": "23:59:59",
    "createdAt": "2026-03-01T10:00:00",
    "category": "EXERCISE",
    "visibility": "PUBLIC",
    "members": [
      {
        "userId": "user_001",
        "nickname": "크루장닉네임",
        "profileImageUrl": "https://...",
        "role": "LEADER",
        "joinedAt": "2026-03-01T10:00:00"
      },
      {
        "userId": "user_002",
        "nickname": "멤버닉네임",
        "profileImageUrl": null,
        "role": "MEMBER",
        "joinedAt": "2026-03-02T14:00:00"
      }
    ],
    "joinable": true,
    "joinBlockedReason": null
  },
  "error": null
}
```

**필드 설명:**
- `joinable`: 현재 유저가 이 크루에 가입 가능한지 여부
- `joinBlockedReason`: 가입 불가 시 사유 (joinable=true이면 null)

**joinBlockedReason 값:**

| 값 | 설명 |
|------|------|
| `ALREADY_MEMBER` | 이미 가입한 크루 |
| `CREW_ENDED` | 크루가 종료(COMPLETED)됨 |
| `CREW_FULL` | 정원 초과 |
| `LATE_JOIN_NOT_ALLOWED` | 중간 가입 비허용 (ACTIVE 크루) |
| `CREW_JOIN_DEADLINE_PASSED` | 참여 마감 기한 초과 |

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 404 | CR006 | 유효하지 않은 초대 코드입니다. | 존재하지 않는 초대코드 |

---

### GET /crews/{crewId}/preview (공개 크루 미리보기)

크루 ID로 공개 크루 정보를 미리 조회한다.
검색 결과에서 상세를 확인할 때 사용하며, 초대코드 미리보기(GET /crews/invite/{inviteCode})와 동일한 응답을 반환한다.
PUBLIC 크루만 조회 가능하다.

**요청 (Request)**
```
GET /crews/{crewId}/preview HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "id": "crew_123",
    "creatorId": "user_001",
    "name": "작심삼일 크루",
    "goal": "매일 운동하기",
    "verificationContent": "운동 완료 인증샷 찍기",
    "verificationType": "PHOTO",
    "maxMembers": 10,
    "currentMembers": 3,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "deadlineTime": "23:59:59",
    "createdAt": "2026-03-01T10:00:00",
    "category": "EXERCISE",
    "visibility": "PUBLIC",
    "members": [
      {
        "userId": "user_001",
        "nickname": "크루장닉네임",
        "profileImageUrl": "https://...",
        "role": "LEADER",
        "joinedAt": "2026-03-01T10:00:00"
      }
    ],
    "joinable": true,
    "joinBlockedReason": null
  },
  "error": null
}
```

**필드 설명:**
- `joinable`: 현재 유저가 이 크루에 가입 가능한지 여부
- `joinBlockedReason`: 가입 불가 시 사유 (joinable=true이면 null)

**joinBlockedReason 값:**

| 값 | 설명 |
|------|------|
| `ALREADY_MEMBER` | 이미 가입한 크루 |
| `CREW_ENDED` | 크루가 종료(COMPLETED)됨 |
| `CREW_FULL` | 정원 초과 |
| `LATE_JOIN_NOT_ALLOWED` | 중간 가입 비허용 (ACTIVE 크루) |
| `CREW_JOIN_DEADLINE_PASSED` | 참여 마감 기한 초과 |

**에러 응답**

| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR022 | 공개 크루가 아닙니다. | PRIVATE 크루에 접근 시도 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |

---

### POST /crews/join (초대코드로 크루 참여)

초대코드를 사용하여 크루에 참여한다. 크루가 RECRUITING 상태이고, 정원이 남아있는 경우에만 참여 가능.

**요청 (Request)**
```
POST /crews/join HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "inviteCode": "ABC123"
}
```

**필드 설명:**
- `inviteCode`: (필수) 크루 초대코드 (6자리)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "userId": "1234567890",
    "crewId": "crew_123",
    "role": "MEMBER",
    "currentMembers": 3,
    "joinedAt": "2026-03-04T10:00:00Z"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR003 | 모집 중인 크루가 아닙니다. | 크루 상태가 RECRUITING이 아님 |
| 400 | CR008 | 크루 참여 마감 기한이 지났습니다. | 중간 가입 불가 시 기한 초과 |
| 404 | CR006 | 유효하지 않은 초대 코드입니다. | 존재하지 않는 초대코드 |
| 409 | CR002 | 크루 정원이 가득 찼습니다. | 정원 초과 |
| 409 | CR004 | 이미 참여 중인 크루입니다. | 중복 참여 |
| 409 | CR023 | 동시 요청 충돌이 발생했습니다. 다시 시도해주세요. | 낙관적 락 재시도 3회 실패 |

---

### GET /crews/{crewId} (크루 상세 조회)

크루 멤버가 상세 화면을 볼 때 사용한다. 멤버가 아니면 403.

**요청 (Request)**
```
GET /crews/{crewId} HTTP/1.1
Authorization: Bearer {accessToken}
```

**응답 (Response)**
```json
{
  "success": true,
  "data": {
    "id": "crew-uuid",
    "creatorId": "user-uuid",
    "name": "새벽 러닝 크루",
    "goal": "매일 아침 5km 러닝",
    "verificationContent": "러닝 완료 후 기록 인증",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 3,
    "status": "ACTIVE",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "inviteCode": "ABC123",
    "createdAt": "2026-03-01T10:00:00",
    "deadlineTime": "23:59:59",
    "category": "EXERCISE",
    "visibility": "PUBLIC",
    "members": [
      {
        "userId": "user-uuid-1",
        "nickname": "크루장닉네임",
        "profileImageUrl": "https://...",
        "role": "LEADER",
        "joinedAt": "2026-03-01T10:00:00",
        "successCount": 2,
        "challengeProgress": {
          "challengeStatus": "IN_PROGRESS",
          "completedDays": 1,
          "targetDays": 3
        }
      },
      {
        "userId": "user-uuid-2",
        "nickname": "멤버닉네임",
        "profileImageUrl": null,
        "role": "MEMBER",
        "joinedAt": "2026-03-02T14:00:00",
        "successCount": 0,
        "challengeProgress": null
      }
    ]
  },
  "error": null
}
```

**필드 설명:**
- `successCount`: 해당 크루에서의 작심삼일(3일 연속 인증) 달성 횟수. 활성 챌린지 유무와 무관하게 항상 표시
- `challengeProgress`: 현재 활성(IN_PROGRESS) 챌린지 진행 상황. 활성 챌린지가 없으면 `null`
  - `challengeStatus`: 챌린지 상태 (IN_PROGRESS, SUCCESS, FAILED, ENDED)
  - `completedDays`: 완료한 일수
  - `targetDays`: 목표 일수 (3)

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 403 | CR009 | 크루 멤버만 조회할 수 있습니다. | 비멤버 접근 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |

---

### POST /crews (크루 생성)

새로운 크루를 생성한다. 생성자는 자동으로 LEADER 역할의 첫 번째 멤버로 추가된다.

**요청 (Request)**
```
POST /crews HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "name": "새벽 러닝 크루",
  "goal": "매일 아침 5km 러닝",
  "verificationContent": "러닝 완료 후 기록 인증",
  "verificationType": "PHOTO",
  "maxMembers": 5,
  "startDate": "2026-03-10",
  "endDate": "2026-03-24",
  "allowLateJoin": true,
  "deadlineTime": "23:59:59",
  "category": "EXERCISE",
  "visibility": "PUBLIC"
}
```

**필드 설명:**
- `name`: (필수) 크루 이름
- `goal`: (필수) 크루 목표
- `verificationContent`: (필수) 인증 내용 (최대 50자)
- `verificationType`: (필수) 인증 방식 — `TEXT` / `PHOTO`
- `maxMembers`: (필수) 최대 정원 (1~10)
- `startDate`: (필수) 크루 시작일 (오늘+1 이후)
- `endDate`: (필수) 크루 종료일 (시작일 + 최소 6일 = 최소 7일 기간 / 최대 `crew.max-duration-days`일, 기본 30일)
- `allowLateJoin`: (선택) 중간 가입 허용 여부 (기본값 false)
- `deadlineTime`: (선택) 일일 인증 마감 시간 (기본값 23:59:59)
- `category`: (필수) 크루 카테고리 — `EXERCISE` / `STUDY` / `LIFESTYLE` / `SELF_DEV` / `ETC`
- `visibility`: (선택) 공개 설정 — `PUBLIC` / `PRIVATE` (기본값 `PRIVATE`)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "crewId": "crew_123",
    "creatorId": "user_456",
    "name": "새벽 러닝 크루",
    "goal": "매일 아침 5km 러닝",
    "verificationContent": "러닝 완료 후 기록 인증",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 1,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "inviteCode": "ABC123",
    "createdAt": "2026-03-09T10:00:00",
    "deadlineTime": "23:59:59",
    "category": "EXERCISE",
    "visibility": "PUBLIC"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR011 | 시작일은 내일 이후여야 합니다. | startDate가 오늘 이전 |
| 400 | CR012 | 종료일은 시작일 이후여야 합니다. | endDate ≤ startDate |
| 400 | CR024 | 크루 기간은 최소 7일 이상이어야 합니다. | (endDate - startDate) < 6일 |
| 400 | CR016 | 크루 기간은 최대 {N}일까지 가능합니다. | (endDate - startDate) > `crew.max-duration-days` |

---

### GET /crews (내 크루 목록 조회)

내가 참여 중인 크루 목록을 조회한다. 홈 화면에서 사용한다.

**요청 (Request)**
```
GET /crews HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "id": "crew_123",
      "name": "새벽 러닝 크루",
      "goal": "매일 아침 5km 러닝",
      "verificationContent": "러닝 완료 후 기록 인증",
      "verificationType": "PHOTO",
      "currentMembers": 3,
      "maxMembers": 5,
      "status": "ACTIVE",
      "startDate": "2026-03-10",
      "endDate": "2026-03-24",
      "createdAt": "2026-03-01T10:00:00",
      "category": "EXERCISE",
      "visibility": "PUBLIC",
      "todayVerified": false,
      "successCount": 2,
      "verifiedDayCount": 8,
      "inviteCode": "A1B2C3",
      "challengeProgress": {
        "challengeStatus": "IN_PROGRESS",
        "completedDays": 1,
        "targetDays": 3
      }
    }
  ],
  "error": null
}
```

**필드 설명:**
- `id`: 크루 ID
- `name`: 크루 이름
- `goal`: 크루 목표
- `verificationContent`: 인증 내용
- `verificationType`: 인증 방식 (`TEXT` / `PHOTO`)
- `currentMembers`: 현재 멤버 수
- `maxMembers`: 최대 정원
- `status`: 크루 상태 (`RECRUITING`, `ACTIVE`, `COMPLETED`)
- `startDate`: 크루 시작일
- `endDate`: 크루 종료일
- `createdAt`: 크루 생성 시각
- `category`: 크루 카테고리 (nullable — 기존 크루는 null)
- `visibility`: 공개 설정 (`PUBLIC` / `PRIVATE`)
- `todayVerified`: 오늘 인증 완료 여부 (boolean)
- `successCount` (int): 요청자가 이 크루에서 달성한 작심삼일(연속 3일 인증 성공) 횟수. `COMPLETED` 크루만 실집계, `RECRUITING`/`ACTIVE`는 `0`(미집계) — ACTIVE 크루의 `0`을 "달성 0회"로 오해 금지(미집계 ≠ 0회 달성).
- `verifiedDayCount` (int): 요청자가 이 크루에서 `APPROVED` 인증을 한 총 일수. `COMPLETED` 크루만 실집계, `RECRUITING`/`ACTIVE`는 `0`(미집계) — ACTIVE 크루의 `0`을 "달성 0회"로 오해 금지(미집계 ≠ 0회 달성).
- `inviteCode`: 크루 초대코드 (6자리 — 본인이 멤버인 크루 목록이므로 노출 안전)
- `challengeProgress` (nullable): 요청자의 현재 진행 중인 챌린지 진행도. 활성(IN_PROGRESS) 챌린지 없으면 `null`.
  - `challengeStatus`: 챌린지 상태 (`IN_PROGRESS`)
  - `completedDays`: 완료한 인증 일수 (0 ~ targetDays-1 — 목표 도달 시 챌린지가 SUCCESS로 전환되어 목록엔 미노출)
  - `targetDays`: 목표 일수 (현재 항상 3)

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |

---

### GET /upload-sessions/{id}/events (SSE 구독 — 업로드 완료 알림)

업로드 세션의 상태 변경을 실시간으로 수신하는 SSE 엔드포인트. 클라이언트가 S3 업로드 후 Lambda가 세션을 COMPLETED로 변경하면 이벤트를 받는다.

**요청 (Request)**
```
GET /upload-sessions/{id}/events HTTP/1.1
Accept: text/event-stream
```

**파라미터:**
- `id`: (필수) 업로드 세션 ID (Long)

**성공 응답 (200 OK, `text/event-stream`)**
```
event: upload-complete
data: COMPLETED
```

**제약 사항:**
- SSE 타임아웃: 60초
- 클라이언트는 fallback으로 폴링 대비 필요

---

### PUT /internal/upload-sessions/complete (Lambda 콜백 — Internal API)

S3 업로드 완료 시 Lambda가 호출하여 업로드 세션을 COMPLETED 상태로 전환하고 SSE 이벤트를 발행한다.

**요청 (Request)**
```
PUT /internal/upload-sessions/complete?imageKey={key} HTTP/1.1
X-Internal-Api-Key: {api-key}
```

**쿼리 파라미터:**
- `imageKey`: (필수) S3 오브젝트 키 (예: `upload-sessions/{userId}/{uuid}.{ext}`)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답:**
- `404 Not Found` — 해당 imageKey의 업로드 세션이 없음
- `403 Forbidden` — API Key 누락 또는 불일치

**보안:**
- `/internal/**` 경로는 `X-Internal-Api-Key` 헤더로 인증 (InternalApiKeyFilter)
- API Key 불일치 시 403 Forbidden 반환
- prod 환경: `internal.api-key` 속성으로 설정

---

### POST /internal/fcm-test (FCM 키 스모크 테스트 — Internal API)

Firebase 서비스계정 키 로테이션·배포 직후, 단건 FCM 발송으로 키 유효성을 즉시 확인한다. (cron/이벤트 기반 발송 경로는 키 무효 시 다음 실행까지 장애를 탐지하지 못하는 공백을 메움)

**요청 (Request)**
```
POST /internal/fcm-test?fcmToken={token} HTTP/1.1
X-Internal-Api-Key: {api-key}
```

**쿼리 파라미터:**
- `fcmToken`: (필수) 테스트 발송 대상 FCM 토큰 (DB의 실제 토큰 권장)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "sent": true,
    "status": "SUCCESS",
    "detail": "발송 성공"
  },
  "error": null
}
```

`status` 값:
- `SUCCESS` — 발송 성공, 키 유효 (`sent: true`)
- `TOKEN_INVALID` — 토큰이 영구 무효 (UNREGISTERED/INVALID_ARGUMENT, `sent: false`). 키 자체는 정상
- `ERROR` — 발송 실패 (`sent: false`, `detail`에 사유). 키 무효 시 3회 재시도(~3초) 후 `FCM_SEND_FAILED`

**에러 응답:**
- `403 Forbidden` — API Key 누락 또는 불일치
- `404 Not Found` — `firebase.enabled=false` 환경(dev/test 및 `FIREBASE_ENABLED` 미설정 prod)에서는 엔드포인트 미존재

**보안:**
- `/internal/**` 경로는 `X-Internal-Api-Key` 헤더로 인증 (InternalApiKeyFilter)
- `firebase.enabled=true` (= FcmAdapter 활성) 환경에서만 빈 등록 — 그 외 404 (dev/test의 NoOp 어댑터로 인한 거짓 성공 차단)
- prod 환경: `internal.api-key` 속성으로 설정

---

### PATCH /crews/{crewId} (크루 수정)

크루장이 RECRUITING 상태 크루의 정보를 부분 수정한다. 최소 1개 이상 필드가 포함되어야 한다.

**요청 (Request)**
```json
PATCH /crews/{crewId} HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "수정된 이름",
  "goal": "수정된 목표",
  "verificationContent": "수정된 인증내용",
  "category": "STUDY",
  "visibility": "PUBLIC"
}
```

**필드 설명:**
- `name`: (선택) 크루 이름
- `goal`: (선택) 크루 목표
- `verificationContent`: (선택) 인증 내용
- `category`: (선택) 크루 카테고리 — `EXERCISE` / `STUDY` / `LIFESTYLE` / `SELF_DEV` / `ETC`
- `visibility`: (선택) 공개 설정 — `PUBLIC` / `PRIVATE`
- 5개 필드 모두 optional (PATCH 시맨틱), 최소 1개 이상 필수
- 빈 문자열("") 또는 공백만 있는 값은 거부

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "crewId": "crew_123",
    "creatorId": "user_456",
    "name": "수정된 이름",
    "goal": "수정된 목표",
    "verificationContent": "수정된 인증내용",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 1,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "inviteCode": "ABC123",
    "createdAt": "2026-03-09T10:00:00",
    "deadlineTime": "23:59:59",
    "category": "STUDY",
    "visibility": "PUBLIC"
  },
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR003 | 모집 중인 크루가 아닙니다. | RECRUITING 상태가 아님 |
| 400 | CR017 | 수정할 필드가 없습니다. | 빈 body / 모든 필드 null |
| 400 | CR018 | 유효하지 않은 값입니다. | 빈 문자열 또는 공백만 있는 값 |
| 403 | CR009 | 크루장만 수정할 수 있습니다. | LEADER가 아님 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 404 | CR021 | 해당 크루의 멤버가 아닙니다. | 크루 미참여 |

---

### DELETE /crews/{crewId} (크루 삭제)

크루장이 혼자이고 인증을 시작하지 않은 크루를 삭제한다. hard delete (DB에서 완전 삭제, FK-safe).

**처리 정책**
- RECRUITING + 혼자(멤버 1명) → 삭제 가능 (기존)
- ACTIVE + 혼자 + 인증 전(`challenges`에 `crew_id` 레코드 없음) → 삭제 가능 (신규)
- ACTIVE + 인증 시작(`challenges`에 `crew_id` 레코드 존재) → 거부 (`CR026`)
- COMPLETED → 거부 (`CR026`)
- 멤버 2명 이상 → 거부 (`CR019`)
- **검증 순서**: 상태 게이트(`CR026`)가 멤버 수 체크(`CR019`)보다 먼저

**요청 (Request)**
```
DELETE /crews/{crewId} HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (204 No Content)**

응답 body 없음.

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR026 | 인증을 시작한 크루는 삭제할 수 없습니다. | ACTIVE+인증시작 / COMPLETED 등 삭제 불가 상태 |
| 403 | CR009 | 크루장만 삭제할 수 있습니다. | LEADER가 아님 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 404 | CR021 | 해당 크루의 멤버가 아닙니다. | 크루 미참여 |
| 409 | CR019 | 크루원이 있는 크루는 삭제할 수 없습니다. | 멤버가 LEADER 본인 외에 존재 |

---

### DELETE /crews/{crewId}/members/me (크루 탈퇴)

크루원(MEMBER)이 크루에서 탈퇴한다. RECRUITING은 무조건 가능, ACTIVE는 챌린지를 한 번도 시작하지 않은 멤버만 가능. LEADER는 탈퇴 불가 (크루 삭제 또는 회원탈퇴 시 자동 위임 사용).

**요청 (Request)**
```
DELETE /crews/{crewId}/members/me HTTP/1.1
Authorization: Bearer <token>
```

**처리 정책:**
- RECRUITING → 무조건 탈퇴 가능
- ACTIVE + 챌린지 미시작(`challenges` 테이블에 (user_id, crew_id) 레코드 없음) → 탈퇴 가능
- ACTIVE + 챌린지 시작 / COMPLETED / FAILED → 거부 (`CR025`)

**성공 응답 (204 No Content)**

응답 body 없음.

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR025 | 진행 중인 크루는 챌린지를 시작하지 않은 멤버만 탈퇴할 수 있습니다. | ACTIVE + 챌린지 시작 / COMPLETED / FAILED |
| 403 | CR020 | 크루장은 탈퇴할 수 없습니다. | LEADER 탈퇴 시도 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 404 | CR021 | 해당 크루의 멤버가 아닙니다. | crew_member 레코드 없음 |

---

### GET /crews/search (크루 검색)

공개(PUBLIC) 크루를 검색한다. 비로그인 사용자도 조회 가능 (permitAll).

**요청 (Request)**
```
GET /crews/search?keyword=러닝&category=EXERCISE&page=0&size=20 HTTP/1.1
```

**쿼리 파라미터:**
- `keyword`: (선택) 검색어 — 크루 이름, 목표에서 LIKE 검색
- `category`: (선택) 카테고리 필터 — `EXERCISE` / `STUDY` / `LIFESTYLE` / `SELF_DEV` / `ETC`
- `page`: (선택) 페이지 번호 (기본값 0)
- `size`: (선택) 페이지 크기 (기본값 20, 최대 50)

**검색 조건:**
- `visibility = PUBLIC`
- AND (`status = RECRUITING` OR (`status = ACTIVE` AND `allowLateJoin = true` AND `endDate - today >= 6`))
- 정렬: `createdAt DESC`

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "crews": [
      {
        "id": "crew_123",
        "name": "새벽 러닝 크루",
        "goal": "매일 아침 5km 러닝",
        "verificationContent": "러닝 완료 후 기록 인증",
        "category": "EXERCISE",
        "verificationType": "PHOTO",
        "allowLateJoin": true,
        "currentMembers": 3,
        "maxMembers": 5,
        "status": "RECRUITING",
        "startDate": "2026-03-10",
        "endDate": "2026-03-24",
        "createdAt": "2026-03-01T10:00:00"
      }
    ],
    "hasNext": false
  },
  "error": null
}
```

**필드 설명:**
- `crews`: 검색 결과 크루 목록
  - `id`: 크루 ID
  - `name`: 크루 이름
  - `goal`: 크루 목표
  - `verificationContent`: 인증 내용
  - `category`: 크루 카테고리
  - `verificationType`: 인증 방식 (`TEXT` / `PHOTO`)
  - `allowLateJoin`: 중간 가입 허용 여부
  - `currentMembers`: 현재 멤버 수
  - `maxMembers`: 최대 정원
  - `status`: 크루 상태 (`RECRUITING`, `ACTIVE`)
  - `startDate`: 크루 시작일
  - `endDate`: 크루 종료일
  - `createdAt`: 크루 생성 시각
- `hasNext`: 다음 페이지 존재 여부

---

### POST /crews/{crewId}/join (공개 크루 가입)

공개 크루에 직접 가입한다. 비공개 크루는 초대코드(POST /crews/join)로만 가입 가능.

**요청 (Request)**
```
POST /crews/{crewId}/join HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "userId": "1234567890",
    "crewId": "crew_123",
    "role": "MEMBER",
    "currentMembers": 4,
    "joinedAt": "2026-03-04T10:00:00Z"
  },
  "error": null
}
```

**비즈니스 규칙:**
- `visibility = PUBLIC`인 크루만 직접 가입 가능
- `status = RECRUITING` 또는 (`status = ACTIVE` AND `allowLateJoin = true` AND 참여 마감 기한 이내)
- 정원 미초과, 중복 참여 불가

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 400 | CR022 | 공개 크루가 아닙니다. | visibility=PRIVATE인 크루 |
| 400 | CR003 | 모집 중인 크루가 아닙니다. | 크루 상태가 가입 불가 |
| 400 | CR008 | 크루 참여 마감 기한이 지났습니다. | 중간 가입 기한 초과 |
| 404 | CR001 | 크루를 찾을 수 없습니다. | 존재하지 않는 crewId |
| 409 | CR002 | 크루 정원이 가득 찼습니다. | 정원 초과 |
| 409 | CR004 | 이미 참여 중인 크루입니다. | 중복 참여 |
| 409 | CR023 | 동시 요청 충돌이 발생했습니다. 다시 시도해주세요. | 낙관적 락 재시도 3회 실패 |

---

### PATCH /users/me/fcm-token (FCM 토큰 등록/갱신)

앱 실행/로그인 시 클라이언트가 FCM 디바이스 토큰을 서버에 등록/갱신한다.

**요청 (Request)**
```
PATCH /users/me/fcm-token HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "fcmToken": "dK1x...FCM디바이스토큰"
}
```

**필드 설명:**
- `fcmToken`: (필수) Firebase Cloud Messaging 디바이스 토큰

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 400 | C001 | 잘못된 입력값입니다. |
| 401 | A003 | 인증이 필요합니다. |

---

### GET /notifications (내 알림 목록 조회)

내 알림을 최신순으로 페이지네이션 조회한다.

**요청 (Request)**
```
GET /notifications?isRead=false&page=0&size=20 HTTP/1.1
Authorization: Bearer <token>
```

**쿼리 파라미터:**
- `isRead`: (선택) 읽음 필터 — 미전달 시 전체, `false`: 안 읽은 알림만, `true`: 읽은 알림만
- `page`: (선택) 페이지 번호 (기본값 0)
- `size`: (선택) 페이지 크기 (기본값 20, 최대 50)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "id": "notif_123",
        "type": "CREW_STARTED",
        "title": "크루 시작!",
        "content": "새벽 러닝 크루가 시작되었습니다.",
        "isRead": false,
        "targetType": "CREW",
        "targetId": "crew_123",
        "createdAt": "2026-03-20T09:00:00"
      }
    ],
    "hasNext": false
  },
  "error": null
}
```

**필드 설명:**
- `notifications`: 알림 목록 (최신순 정렬)
  - `id`: 알림 ID
  - `type`: 알림 타입 (CREW_STARTED, REMINDER 등)
  - `title`: 알림 제목
  - `content`: 알림 내용
  - `isRead`: 읽음 여부
  - `targetType`: 알림 대상 타입 (CREW 등)
  - `targetId`: 알림 대상 ID (nullable)
  - `createdAt`: 알림 생성 시각
- `hasNext`: 다음 페이지 존재 여부

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### GET /notifications/unread-count (안 읽은 알림 수 조회)

읽지 않은 알림 수를 조회한다. 뱃지 표시용.

**요청 (Request)**
```
GET /notifications/unread-count HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "count": 5
  },
  "error": null
}
```

**필드 설명:**
- `count`: 안 읽은 알림 수

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### PATCH /notifications/{id}/read (알림 읽음 처리)

알림을 읽음 상태로 변경한다. 본인 알림만 처리 가능.

**요청 (Request)**
```
PATCH /notifications/{id}/read HTTP/1.1
Authorization: Bearer <token>
```

**경로 파라미터:**
- `id`: (필수) 알림 ID

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |
| 404 | S001 | 알림을 찾을 수 없습니다. | 존재하지 않거나 본인 알림 아님 |

---

### DELETE /notifications (알림 전체 삭제)

본인의 알림을 전체 삭제한다. Hard Delete. 0건이어도 200 OK (멱등).

**요청 (Request)**
```
DELETE /notifications HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### PATCH /notifications/read-all (알림 전체 읽음)

본인의 안 읽은 알림을 전체 읽음 처리한다. 이미 전부 읽은 상태여도 200 OK (멱등).

**요청 (Request)**
```
PATCH /notifications/read-all HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### GET /invite/{inviteCode} (초대 링크 랜딩 페이지)

초대코드를 포함한 HTML 랜딩 페이지를 반환한다. 인증 불필요.

**요청 (Request)**
```
GET /invite/ABC123 HTTP/1.1
Host: triagain.kr
Accept: text/html
```

**경로 파라미터:**
- `inviteCode`: 6자리 초대코드 (URL에서 추출, DB 검증 없음)

**성공 응답 (200 OK)**
```
Content-Type: text/html;charset=UTF-8

[HTML 랜딩 페이지 — Thymeleaf 렌더링]
```

**보안:**
- Spring Security `permitAll()` 적용: `/invite/**`, `/images/**`, `/css/**`, `/feedback`
- 기존 API 인증 흐름에 영향 없음

**정적 리소스:**
- `/images/logo.png` — TriAgain 로고 (frontend에서 복사)

**참고:**
- DB 조회 없음 — URL의 inviteCode를 그대로 Thymeleaf Model에 담아 템플릿에 전달
- 잘못된 코드 별도 검증 없음 — 앱에서 입력 시 검증됨
- Phase 2: 딥링크(App Links / Universal Links) 추가 예정

---

## Habit (솔로 모드)

> 유저가 크루 없이 '습관'을 등록하고 3일짜리 '작심' 사이클을 반복하는 개인 모드. 전 엔드포인트 `Authorization: Bearer <token>` 필수(미인증 시 A003). 응답 형식은 공통 래핑(`{success, data, error}`)을 따른다.
> 모든 변경 API(등록 제외)는 habit을 `status <> 'ENDED'` 조건으로 조회 — ENDED 습관 접근 시 HB001.

### POST /habits (습관 등록)

**요청 (Request)**
```json
POST /habits HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "매일 물 2L",
  "verificationType": "TEXT",
  "deadlineTime": "23:59:59"
}
```

**필드 설명:**
- `name`: 필수, 1~50자
- `verificationType`: 필수, `TEXT` | `PHOTO`
- `deadlineTime`: 선택, 기본 `23:59:59` (v1 FE는 미노출 — 서버 스펙만 지원)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "habitId": "HBIT-a1b2c3d4e5f60708",
    "name": "매일 물 2L",
    "verificationType": "TEXT",
    "deadlineTime": "23:59:59",
    "status": "ACTIVE",
    "createdAt": "2026-07-05T14:30:00",
    "endedAt": null
  },
  "error": null
}
```

**실패 응답**
```json
// 400 Bad Request - 잘못된 입력값
{
  "code": "INVALID_INPUT",
  "message": "잘못된 입력값입니다."
}

// 401 Unauthorized
{
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다."
}
```

**핵심 규칙:**
- 습관 등록은 사이클을 만들지 않는다 — FE가 등록 직후 `POST /habits/{habitId}/cycles`를 이어 호출

---

### GET /habits (내 습관 목록 조회)

홈 탭(오늘 할 일/오늘 완료/예정)의 솔로 데이터 소스. `status IN ('ACTIVE','PAUSED')`인 본인 습관 전체(ENDED는 `GET /habits/archived`로 분리). 크루 `GET /crews`와 병렬로 홈에서 병합.

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "habitId": "HBIT-a1b2c3d4e5f60708",
      "name": "매일 물 2L",
      "verificationType": "TEXT",
      "deadlineTime": "23:59:59",
      "status": "ACTIVE",
      "successCount": 4,
      "todayVerified": false,
      "activeCycle": {
        "cycleId": "HCYC-0102030405060708",
        "cycleNumber": 7,
        "completedDays": 1,
        "targetDays": 3,
        "status": "IN_PROGRESS",
        "startDate": "2026-07-04",
        "deadline": "2026-07-07T23:59:59"
      }
    },
    {
      "habitId": "HBIT-b2c3d4e5f6071819",
      "name": "달리기 30분",
      "verificationType": "PHOTO",
      "deadlineTime": "23:59:59",
      "status": "PAUSED",
      "successCount": 2,
      "todayVerified": false,
      "activeCycle": null
    }
  ],
  "error": null
}
```

**필드 설명:**
- `successCount`: SUCCESS 사이클 COUNT (별도 캐시 컬럼 없음)
- `todayVerified`: 오늘 인증 존재 여부
- `activeCycle`: IN_PROGRESS 사이클 없으면 null (FAILED/SUCCESS 직후·PAUSED·등록 직후). `startDate`가 미래면 FE는 "내일부터 시작" 표기
- 정렬: `createdAt` 오름차순
- 단건 조회 `GET /habits/{id}`는 v1 미제공 (목록 payload로 충분)

---

### GET /habits/archived (지난기록 — 종료한 습관)

마이페이지 지난기록 화면의 솔로 섹션 데이터 소스. `status = 'ENDED'`인 본인 습관, `endedAt` 내림차순(최근 종료순).

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": [
    {
      "habitId": "HBIT-a1b2c3d4e5f60708",
      "name": "매일 물 2L",
      "verificationType": "TEXT",
      "successCount": 6,
      "endedAt": "2026-07-05T21:30:00"
    }
  ],
  "error": null
}
```

**필드 설명:**
- `successCount`: 종료 시점까지 누적 성공(SUCCESS 사이클 COUNT)
- 읽기전용 카드 — 재개/재시작 액션 없음. `activeCycle`·`todayVerified`는 무의미하므로 미포함

**실패 응답**
```json
// 401 Unauthorized
{
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다."
}
```

---

### PATCH /habits/{habitId} (습관 이름 수정)

**요청 (Request)**
```json
PATCH /habits/HBIT-a1b2c3d4e5f60708 HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "매일 물 3L"
}
```
- `name`만 수정 가능(v1) — `verificationType`/`deadlineTime` 변경 불가

**성공 응답 (200 OK)** — POST /habits와 동일한 habit payload

**실패 응답**
```json
// 404 Not Found
{
  "code": "HABIT_NOT_FOUND",
  "message": "습관을 찾을 수 없습니다."
}

// 403 Forbidden
{
  "code": "HABIT_ACCESS_DENIED",
  "message": "본인 습관만 이용할 수 있습니다."
}

// 400 Bad Request - 잘못된 입력값
{
  "code": "INVALID_INPUT",
  "message": "잘못된 입력값입니다."
}
```

---

### POST /habits/{habitId}/end (습관 종료 — 아카이브)

기존 '삭제'를 대체(D10) — 데이터를 지우지 않고 status만 `ENDED`로 전이하므로 `DELETE`가 아닌 하위 액션 `POST`로 설계.

**성공 응답 (200 OK)** — habit payload (`status=ENDED`, `endedAt` set). IN_PROGRESS 사이클이 있으면 같은 트랜잭션에서 `fail()` 처리. 종료 후 습관은 `GET /habits` 목록에서 사라지고 `GET /habits/archived`에 노출

**실패 응답**
```json
// 404 Not Found
{
  "code": "HABIT_NOT_FOUND",
  "message": "습관을 찾을 수 없습니다."
}

// 403 Forbidden
{
  "code": "HABIT_ACCESS_DENIED",
  "message": "본인 습관만 이용할 수 있습니다."
}
```

**핵심 규칙:**
- 터미널 — ENDED 습관은 재개/재시작/재종료 불가. 다시 하려면 새 습관 등록(성공 카운트 0부터)
- v1은 완전 삭제(하드 삭제) 없음 — 종료가 기록 보존까지 겸함

---

### POST /habits/{habitId}/pause · POST /habits/{habitId}/resume (습관 멈춤 · 재개)

**성공 응답 (200 OK)** — habit payload (status 변경 반영)

**실패 응답**
```json
// 400 Bad Request - pause 전용, IN_PROGRESS 사이클 존재
{
  "code": "HABIT_PAUSE_NOT_ALLOWED",
  "message": "진행 중인 작심이 있으면 멈출 수 없습니다."
}

// 404 Not Found
{
  "code": "HABIT_NOT_FOUND",
  "message": "습관을 찾을 수 없습니다."
}

// 403 Forbidden
{
  "code": "HABIT_ACCESS_DENIED",
  "message": "본인 습관만 이용할 수 있습니다."
}
```

**핵심 규칙:**
- pause: IN_PROGRESS 사이클이 없을 때만 가능(HB004). 알림 없음·기록 보존·재개 가능
- resume: ACTIVE 상태에서 호출 시 no-op 200

---

### POST /habits/{habitId}/cycles (사이클 시작 — 첫 시작/재시작 통합)

**요청 (Request)**
```json
POST /habits/HBIT-a1b2c3d4e5f60708/cycles HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "startOption": "TODAY"
}
```
- `startOption`: 선택, `TODAY`(기본) | `TOMORROW`

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "cycleId": "HCYC-0102030405060708",
    "cycleNumber": 7,
    "completedDays": 0,
    "targetDays": 3,
    "status": "IN_PROGRESS",
    "startDate": "2026-07-05",
    "deadline": "2026-07-08T23:59:59"
  },
  "error": null
}
```
- `startDate = today | today+1`, `deadline = startDate.plusDays(3).atTime(habit.deadlineTime)`
- `cycleNumber = findMaxCycleNumber(habitId) + 1` (첫 시작이면 1)

**실패 응답**
```json
// 409 Conflict - 이미 진행 중인 작심이 있음 (더블탭은 유니크 제약 catch 후 기존 사이클을 200으로 반환 — 멱등 처리)
{
  "code": "HABIT_CYCLE_ALREADY_IN_PROGRESS",
  "message": "이미 진행 중인 작심이 있습니다."
}

// 400 Bad Request - TODAY인데 오늘 마감+유예 경과
{
  "code": "VERIFICATION_DEADLINE_EXCEEDED",
  "message": "인증 마감 시간이 지났습니다."
}

// 409 Conflict - TODAY인데 오늘 이미 인증함 (좀비 사이클 방지)
{
  "code": "VERIFICATION_ALREADY_EXISTS",
  "message": "오늘은 이미 인증을 완료했어요. 내일부터 시작할 수 있어요."
}

// 400 Bad Request - PAUSED 습관
{
  "code": "HABIT_NOT_ACTIVE",
  "message": "멈춘 습관입니다. 재개 후 시작할 수 있습니다."
}

// 404 Not Found
{
  "code": "HABIT_NOT_FOUND",
  "message": "습관을 찾을 수 없습니다."
}
```

**핵심 규칙:**
- `startOption=TODAY`는 마감 전(V002) + 오늘 미인증(V003) 두 가드를 모두 통과해야 함. `TOMORROW`는 두 가드 모두 무관하게 항상 허용

---

### DELETE /habits/{habitId}/cycles/current (시작 전 사이클 취소)

**성공 응답 (204 No Content)** — `today < startDate`인 IN_PROGRESS 사이클을 hard delete (시작 전엔 인증이 존재할 수 없어 자식 행 없음)

**실패 응답**
```json
// 400 Bad Request - 활성 사이클 없음
{
  "code": "HABIT_CYCLE_NOT_IN_PROGRESS",
  "message": "진행 중인 작심이 없습니다."
}

// 400 Bad Request - 시작일 도래 후 취소 시도
{
  "code": "HABIT_CYCLE_CANCEL_NOT_ALLOWED",
  "message": "시작일이 지난 작심은 취소할 수 없습니다."
}

// 404 Not Found
{
  "code": "HABIT_NOT_FOUND",
  "message": "습관을 찾을 수 없습니다."
}
```

---

### POST /habits/{habitId}/verifications (솔로 인증 생성)

**요청 (Request)**
```json
POST /habits/HBIT-a1b2c3d4e5f60708/verifications HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "uploadSessionId": 123,
  "textContent": "오늘도 물 2L 클리어"
}
```
- `uploadSessionId`: PHOTO 습관 필수, TEXT 습관은 보내지 않음
- `textContent`: TEXT 습관 필수, PHOTO 습관 선택
- `Idempotency-Key` 헤더 미도입 — `uk_habit_verifications_habit_date` 유니크 제약이 더블카운트를 원천 차단

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "verificationId": "HVRF-1122334455667788",
    "habitCycleId": "HCYC-0102030405060708",
    "habitId": "HBIT-a1b2c3d4e5f60708",
    "imageUrl": null,
    "textContent": "오늘도 물 2L 클리어",
    "targetDate": "2026-07-05",
    "attemptNumber": 2,
    "cycle": {
      "completedDays": 2,
      "targetDays": 3,
      "status": "IN_PROGRESS"
    }
  },
  "error": null
}
```
- `cycle.status == "SUCCESS"`면 FE가 성공 연출(작심 1회 달성)을 노출

**실패 응답**
```json
// 400 Bad Request - 활성 사이클 없음(FAILED 후 등)
{
  "code": "HABIT_CYCLE_NOT_IN_PROGRESS",
  "message": "진행 중인 작심이 없습니다."
}

// 400 Bad Request - 시작일 전(TOMORROW 사이클 사전 인증)
{
  "code": "HABIT_CYCLE_NOT_STARTED",
  "message": "아직 시작일이 되지 않은 작심입니다."
}

// 400 Bad Request - 멈춘 습관
{
  "code": "HABIT_NOT_ACTIVE",
  "message": "멈춘 습관입니다. 재개 후 이용할 수 있습니다."
}

// 400 Bad Request - 다른 습관용으로 발급된 업로드 세션
{
  "code": "HABIT_UPLOAD_SESSION_MISMATCH",
  "message": "다른 습관용으로 발급된 업로드 세션입니다."
}

// 409 Conflict - 오늘 이미 인증함
{
  "code": "VERIFICATION_ALREADY_EXISTS",
  "message": "이미 해당 날짜에 인증이 존재합니다."
}

// 400 Bad Request - 마감 초과 또는 기대 슬롯 불일치(자정 넘긴 grace 인증 포함)
{
  "code": "VERIFICATION_DEADLINE_EXCEEDED",
  "message": "인증 마감 시간이 지났습니다."
}

// 400 Bad Request - 크루용 세션 교차 사용
{
  "code": "UPLOAD_SESSION_CREW_MISMATCH",
  "message": "업로드 세션의 크루 정보가 일치하지 않습니다."
}

// 404 Not Found
{
  "code": "HABIT_NOT_FOUND",
  "message": "습관을 찾을 수 없습니다."
}

// 403 Forbidden
{
  "code": "HABIT_ACCESS_DENIED",
  "message": "본인 습관만 이용할 수 있습니다."
}
```

**핵심 규칙:**
- 인증 시 `targetDate == cycle.startDate + completedDays`(기대 슬롯) 강제 — 위반 시 V002. 건너뛴 날 마스킹, 자정 넘긴 grace 인증(예: 00:02 "어제 것")을 원천 차단("자정 넘기면 그 날은 실패" 확정)
- 저장 + `cycle.recordCompletion()`은 같은 트랜잭션(원자적). `attemptNumber = completedDays + 1`
- 가드 순서: 습관 존재+소유자 → 활성(ACTIVE) → 사이클 IN_PROGRESS → 시작일 도래 → 기대 슬롯+중복 → 타입/세션 → 마감

---

## TODO (구현 시 추가 예정)

### Moderation Context
- POST /verifications/{id}/reports — 신고

### Support Context
- POST /verifications/{id}/reactions — 반응 (이모지)
