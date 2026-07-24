# API 명세 — 인증/유저 (Auth / User)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 API 계약 정본이다. 코드보다 이 문서를 먼저 수정한다.

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

