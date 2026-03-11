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

**요청 (Request)**
```json
POST /upload-sessions HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "challengeId": "chal_123",
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
  "identityToken": "Apple_SDK에서_받은_identity_token"
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
  "termsAgreed": true
}
```

**필드 설명:**
- `identityToken`: (필수) Apple SDK에서 받은 Identity Token (JWT)
- `appleId`: (필수) POST /auth/apple 응답의 `appleId` 값
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
  - `status`: 챌린지 상태 (IN_PROGRESS, COMPLETED, FAILED)
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
  - `status`: 챌린지 상태 (IN_PROGRESS, COMPLETED, FAILED)
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
    "name": "작심삼일 크루",
    "goal": "매일 운동하기",
    "verificationType": "PHOTO",
    "maxMembers": 10,
    "currentMembers": 3,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "deadlineTime": "23:59:59",
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
  "data": {
    "id": "crew-uuid",
    "creatorId": "user-uuid",
    "name": "새벽 러닝 크루",
    "goal": "매일 아침 5km 러닝",
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

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 403 | CR009 | 크루 멤버만 접근할 수 있습니다. | 비멤버 접근 |
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
  "verificationType": "PHOTO",
  "maxMembers": 5,
  "startDate": "2026-03-10",
  "endDate": "2026-03-24",
  "allowLateJoin": true,
  "deadlineTime": "23:59:59"
}
```

**필드 설명:**
- `name`: (필수) 크루 이름
- `goal`: (필수) 크루 목표
- `verificationType`: (필수) 인증 방식 — `TEXT` / `PHOTO`
- `maxMembers`: (필수) 최대 정원 (1~10)
- `startDate`: (필수) 크루 시작일
- `endDate`: (필수) 크루 종료일
- `allowLateJoin`: (선택) 중간 가입 허용 여부 (기본값 false)
- `deadlineTime`: (선택) 일일 인증 마감 시간 (기본값 23:59:59)

**성공 응답 (201 Created)**
```json
{
  "success": true,
  "data": {
    "crewId": "crew_123",
    "creatorId": "user_456",
    "name": "새벽 러닝 크루",
    "goal": "매일 아침 5km 러닝",
    "verificationType": "PHOTO",
    "maxMembers": 5,
    "currentMembers": 1,
    "status": "RECRUITING",
    "startDate": "2026-03-10",
    "endDate": "2026-03-24",
    "allowLateJoin": true,
    "inviteCode": "ABC123",
    "createdAt": "2026-03-09T10:00:00",
    "deadlineTime": "23:59:59"
  },
  "error": null
}
```

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
event: upload-completed
data: {"uploadSessionId": 123, "status": "COMPLETED"}
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

## TODO (구현 시 추가 예정)

### Crew Context
- GET /crews — 크루 목록 조회

### Moderation Context
- POST /verifications/{id}/reports — 신고

### Support Context
- POST /verifications/{id}/reactions — 반응 (이모지)
