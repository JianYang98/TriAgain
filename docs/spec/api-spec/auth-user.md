# API 명세 — 인증/유저 (Auth / User)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 Auth/User API 계약 정본이다.
> User Context 개요는 [`../user.md`](../user.md)를 참고한다.

---

## 1. 공통 계약

### 응답 형식

성공:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "A003",
    "message": "인증이 필요합니다."
  }
}
```

### 인증

| 범위 | 현행 인증 |
|---|---|
| `/auth/**` | `permitAll` |
| `/users/**` | `Authorization: Bearer <accessToken>` 필수 |

- 운영 환경에서 `/users/**`는 유효한 Access Token과 현재 `token_version`이 일치해야 한다.
- `/users/**`의 인증 실패는 `401 A003`이다.
- `!prod` 환경만 개발·테스트를 위해 `X-User-Id` fallback을 지원한다.
- 요청 body의 필수 필드 누락·blank·타입 오류 등 Bean Validation 실패는 별도 매핑이 없으면
  `400 C001`로 응답한다.

---

## 2. 인증 API

### POST /auth/kakao

카카오 Access Token으로 사용자를 확인한다. 활성 사용자는 JWT를 받고, 신규·탈퇴 사용자는
가입에 필요한 카카오 정보만 받는다. 신규·탈퇴 사용자는 이 API에서 생성·재활성화되지 않는다.

**인증**: 불필요

**요청**

```json
{
  "kakaoAccessToken": "카카오 SDK에서 받은 access token"
}
```

**기존 사용자 응답 — 200 OK (`data`)**

```json
{
  "isNewUser": false,
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 1800,
  "user": {
    "id": "1234567890",
    "nickname": "김철수",
    "profileImageUrl": "https://example.com/profile.jpg"
  },
  "kakaoId": null,
  "kakaoProfile": null
}
```

**신규·탈퇴 사용자 응답 — 200 OK (`data`)**

```json
{
  "isNewUser": true,
  "accessToken": null,
  "refreshToken": null,
  "accessTokenExpiresIn": null,
  "user": null,
  "kakaoId": "1234567890",
  "kakaoProfile": {
    "nickname": "카카오닉네임",
    "email": "user@kakao.com",
    "profileImageUrl": "https://example.com/profile.jpg"
  }
}
```

- 기존 사용자 로그인에서는 email과 profileImageUrl을 동기화하지 않는다.
- 카카오 Access Token은 사용자 확인에만 사용하고 저장하지 않는다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `kakaoAccessToken` 누락·blank |
| 401 | A001 | 카카오 토큰 무효·만료 |
| 502 | A002 | 카카오 API 호출 실패 |

### POST /auth/signup

카카오 신규 가입 또는 탈퇴 계정 재활성화를 수행하고 JWT를 발급한다.

**인증**: 불필요

**요청**

```json
{
  "kakaoAccessToken": "카카오 SDK에서 받은 access token",
  "kakaoId": "1234567890",
  "nickname": "내닉네임",
  "termsAgreed": true
}
```

- `kakaoId`는 `/auth/kakao`에서 받은 값이며 토큰의 실제 소유자 ID와 일치해야 한다.
- `nickname`은 2~12자의 한글·영문·숫자·언더스코어만 허용하고, 앞뒤 공백은 `String.trim()` 기준(U+0020 이하)으로 트림한 뒤 검증·저장한다 — `"  닉네임  "`은 `"닉네임"`이 된다. 공백만으로 된 값과 비ASCII 공백의 판정은 `docs/spec/user.md`의 닉네임 표를 따르며, 가입 두 경로와 `PATCH /users/me/nickname`이 동일하게 동작한다.
- `termsAgreed`는 반드시 true여야 한다.

**응답 — 201 Created (`data`)**

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 1800,
  "user": {
    "id": "1234567890",
    "nickname": "내닉네임",
    "profileImageUrl": "https://example.com/profile.jpg"
  }
}
```

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 필수 필드 누락·blank |
| 400 | U004 | `nickname`이 `Character.isWhitespace` 기준 비ASCII 공백(U+3000 등)만으로 구성 |
| 400 | U005 | `termsAgreed=false` |
| 400 | U007 | 닉네임 형식 불일치 |
| 400 | U008 | `kakaoId`와 토큰 소유자 불일치 |
| 401 | A001 | 카카오 토큰 무효·만료 |
| 409 | U006 | 이미 가입된 활성 사용자 |
| 502 | A002 | 카카오 API 호출 실패 |

### POST /auth/apple

Apple Identity Token으로 사용자를 확인한다. 활성 사용자는 JWT를 받고, 신규·탈퇴 사용자는
가입에 필요한 Apple 정보만 받는다.

**인증**: 불필요

**요청**

```json
{
  "identityToken": "Apple SDK에서 받은 identity token",
  "authorizationCode": "Apple SDK에서 받은 authorization code"
}
```

- `identityToken`은 필수다.
- `authorizationCode`는 선택이다. 기존 사용자가 보내면 Apple refresh token backfill을
  best-effort로 시도하며 실패해도 로그인은 계속한다.
- 신규·탈퇴 사용자 분기에서는 backfill하지 않고 회원가입 시 처리한다.

**기존 사용자 응답 — 200 OK (`data`)**

```json
{
  "isNewUser": false,
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 1800,
  "user": {
    "id": "001234.abcdef.5678",
    "nickname": "유저닉네임",
    "profileImageUrl": null
  },
  "appleId": null,
  "email": null
}
```

**신규·탈퇴 사용자 응답 — 200 OK (`data`)**

```json
{
  "isNewUser": true,
  "accessToken": null,
  "refreshToken": null,
  "accessTokenExpiresIn": null,
  "user": null,
  "appleId": "001234.abcdef.5678",
  "email": "user@privaterelay.appleid.com"
}
```

- Apple은 email을 최초 인증에서만 제공할 수 있으며 프로필 이미지는 제공하지 않는다.
- 기존 사용자 로그인에서 Apple이 email을 제공한 경우에만 저장된 email을 동기화한다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `identityToken` 누락·blank |
| 401 | A005 | Apple 토큰 무효·만료 |
| 502 | A006 | Apple 토큰 검증 과정 실패 |

### POST /auth/apple-signup

Apple 신규 가입 또는 탈퇴 계정 재활성화를 수행하고 JWT를 발급한다.

**인증**: 불필요

**요청**

```json
{
  "identityToken": "Apple SDK에서 받은 identity token",
  "appleId": "001234.abcdef.5678",
  "nickname": "내닉네임",
  "termsAgreed": true,
  "authorizationCode": "Apple SDK에서 받은 authorization code"
}
```

- `appleId`는 `/auth/apple`에서 받은 값이며 identity token의 `sub`와 일치해야 한다.
- `nickname`은 2~12자의 한글·영문·숫자·언더스코어만 허용하고, 앞뒤 공백은 `String.trim()` 기준(U+0020 이하)으로 트림한 뒤 검증·저장한다 — `"  닉네임  "`은 `"닉네임"`이 된다. 공백만으로 된 값과 비ASCII 공백의 판정은 `docs/spec/user.md`의 닉네임 표를 따르며, 가입 두 경로와 `PATCH /users/me/nickname`이 동일하게 동작한다.
- `authorizationCode`는 필수이며 1회용이다. Apple refresh token으로 교환한 뒤 암호화하여 저장한다.
- authorization code 교환이 실패하면 사용자 생성·재활성화를 진행하지 않는다.

**응답 — 201 Created (`data`)**

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 1800,
  "user": {
    "id": "001234.abcdef.5678",
    "nickname": "내닉네임",
    "profileImageUrl": null
  }
}
```

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 필수 필드 누락·blank |
| 400 | U004 | `nickname`이 `Character.isWhitespace` 기준 비ASCII 공백(U+3000 등)만으로 구성 |
| 400 | U005 | `termsAgreed=false` |
| 400 | U007 | 닉네임 형식 불일치 |
| 400 | U009 | `appleId`와 identity token의 `sub` 불일치 |
| 401 | A005 | Apple 토큰 무효·만료 |
| 409 | U006 | 이미 가입된 활성 사용자 |
| 502 | A006 | Apple 토큰 검증 과정 실패 |
| 502 | A007 | authorization code 교환 실패 |

### POST /auth/refresh

Refresh Token으로 새 Access Token만 발급한다. Refresh Token rotation은 하지 않는다.

**인증**: 불필요

**요청**

```json
{
  "refreshToken": "eyJ..."
}
```

**응답 — 200 OK (`data`)**

```json
{
  "accessToken": "eyJ...",
  "accessTokenExpiresIn": 1800
}
```

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `refreshToken` 누락·blank |
| 401 | A004 | 토큰 무효·만료·타입 불일치 또는 `token_version` 불일치 |
| 404 | U001 | 활성 사용자를 찾을 수 없음 |

### POST /auth/logout

Phase 1 로그아웃은 서버 상태를 변경하지 않는 no-op이다. 클라이언트가 로컬 Access·Refresh Token을
삭제해야 한다.

**인증**: 불필요 — 현재 `/auth/**`가 `permitAll`이고 Controller도 인증 사용자를 받지 않는다.

**요청 body**: 없음

**응답 — 200 OK**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

## 3. 사용자 API

이 절의 모든 API는 Bearer Access Token 인증이 필요하며 인증 실패 시 `401 A003`을 반환한다.

### GET /users/me

내 프로필을 조회한다.

**응답 — 200 OK (`data`)**

```json
{
  "id": "1234567890",
  "nickname": "내닉네임",
  "profileImageUrl": "https://example.com/profile.jpg",
  "email": "user@kakao.com"
}
```

`profileImageUrl`과 `email`은 null일 수 있다.

### PATCH /users/me/nickname

닉네임을 변경하고 변경된 전체 프로필을 반환한다.

**요청**

```json
{
  "nickname": "새닉네임"
}
```

- `nickname`은 2~12자의 한글·영문·숫자·언더스코어만 허용하고, 앞뒤 공백은 `String.trim()` 기준(U+0020 이하)으로 트림한 뒤 검증·저장한다 — `"  닉네임  "`은 `"닉네임"`이 된다. 공백만으로 된 값과 비ASCII 공백의 판정은 `docs/spec/user.md`의 닉네임 표를 따르며, 가입 두 경로와 `PATCH /users/me/nickname`이 동일하게 동작한다.

**응답 — 200 OK (`data`)**

```json
{
  "id": "1234567890",
  "nickname": "새닉네임",
  "profileImageUrl": "https://example.com/profile.jpg",
  "email": "user@kakao.com"
}
```

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `nickname` 누락·blank |
| 400 | U004 | `nickname`이 `Character.isWhitespace` 기준 비ASCII 공백(U+3000 등)만으로 구성 |
| 400 | U007 | 2~12자 한글·영문·숫자·언더스코어 규칙 불일치 |

### POST /users/me/profile-image/upload-session

프로필 이미지를 S3에 직접 업로드할 presigned URL을 발급한다.

**요청**

```json
{
  "fileName": "profile.jpg",
  "fileType": "image/jpeg",
  "fileSize": 512000
}
```

- `fileType`: `image/jpeg`, `image/png`, `image/webp`만 허용
- `fileSize`: 1 byte 이상 5 MiB 이하
- `fileName`: 필수 입력이지만 현재 저장 키 생성에는 사용하지 않는다.

**응답 — 201 Created (`data`)**

```json
{
  "presignedUrl": "https://bucket.example/profiles/1234567890/uuid.jpg?...",
  "imageUrl": "https://bucket.example/profiles/1234567890/uuid.jpg",
  "expiresAt": "2026-08-20T15:00:00"
}
```

- presigned URL 유효기간은 15분이다.
- 저장 키는 서버가 `profiles/{userId}/{uuid}.{ext}` 형식으로 생성한다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 필수 필드 누락·blank 또는 타입 오류 |
| 400 | V007 | 지원하지 않는 MIME 타입 |
| 400 | V008 | 파일 크기가 0 이하 또는 5 MiB 초과 |

### PATCH /users/me/profile-image

S3 업로드 후 이미지 URL을 내 프로필에 반영한다.

**요청**

```json
{
  "imageUrl": "https://bucket.example/profiles/1234567890/uuid.jpg"
}
```

- `imageUrl=null`이면 기본 이미지 상태로 초기화한다.
- 값이 있으면 설정된 S3 버킷 도메인, `profiles/{userId}/` 소유 경로,
  UUID 형식 키와 `jpg|png|webp` 확장자를 검증한다.

**응답 — 200 OK (`data`)**

```json
{
  "id": "1234567890",
  "nickname": "내닉네임",
  "profileImageUrl": "https://bucket.example/profiles/1234567890/uuid.jpg",
  "email": "user@kakao.com"
}
```

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | U011 | 허용된 S3 사용자 경로가 아닌 이미지 주소 |

### PATCH /users/me/fcm-token

앱 실행·로그인 시 사용자의 FCM 토큰을 등록하거나 덮어쓴다.

**요청**

```json
{
  "fcmToken": "fcm-token-abc123"
}
```

- `fcmToken`은 blank가 아니어야 하며 최대 500자다.
- 사용자별로 현재 토큰 하나를 저장한다.

**응답 — 200 OK**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `fcmToken` 누락·blank 또는 500자 초과 |

### DELETE /users/me

사용자를 soft delete하고 개인정보·크루 멤버십을 정리하며 기존 JWT를 무효화한다.

**요청 body**: 없음

**처리**

1. Apple 사용자이고 저장된 Apple refresh token이 있으면 트랜잭션 밖에서 revoke를 시도한다.
2. revoke 실패는 경고로 남기고 탈퇴는 계속한다.
3. 모든 활성 챌린지를 종료하고 크루 멤버십을 정리한다.
4. 혼자인 크루장은 크루를 삭제하고, 멤버가 있는 크루장은 가장 오래된 멤버에게 위임한다.
5. 개인정보와 FCM·Apple refresh token을 비우고 `deleted_at`을 기록한다.
6. `token_version`을 증가시켜 기존 Access·Refresh Token을 무효화한다.

**응답 — 200 OK**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

- 이미 탈퇴한 사용자는 활성 사용자 조회에서 제외된다. 기존 JWT로 재요청하면 운영에서는
  인증 필터에서 `401 A003`, `!prod`의 `X-User-Id` fallback에서는 서비스에서 `404 U001`로
  처리된다. `U010`은 정의되어 있지만 현재 Controller 경로에서는 도달하지 않는다.
