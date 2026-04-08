# [수정 지시] 프론트 에이전트에게

> **작성일**: 2026-04-08
> **근거**: App Store Review Guideline 5.1.1(v) — Sign in with Apple 사용자는 회원탈퇴 시 Apple 연결 해제(`/auth/revoke`)가 필수
> **백엔드 정본**: `docs/spec/biz-logic.md` 1.12 / 1.14, `docs/spec/api-spec.md` `/auth/apple`, `/auth/apple-signup`, `DELETE /users/me`

---

## 결정 사항 요약

- Apple 회원탈퇴 시 백엔드가 Apple `/auth/revoke`를 호출하려면 **Apple OAuth refresh_token**이 DB에 저장되어 있어야 한다.
- refresh_token을 발급받으려면 Apple `/auth/token` 호출이 필요하고, 이 호출에는 클라이언트가 받은 `authorizationCode`가 필요하다.
- 따라서 **Apple 로그인/회원가입 요청에 `authorizationCode`를 포함**해야 한다.
- 프론트는 다른 변경 없이 `authorizationCode` 필드만 추가해 보내면 된다. revoke 호출 자체는 백엔드가 알아서 한다.

---

## 백엔드 변경 요약 (참고)

| 항목 | 변경 |
|------|------|
| `POST /auth/apple` | request에 `authorizationCode` 옵셔널 필드 추가. 기존 사용자가 보내면 backfill 저장 |
| `POST /auth/apple-signup` | request에 `authorizationCode` **필수** 필드 추가. 누락 시 회원가입 실패 |
| `DELETE /users/me` | provider=APPLE && apple_refresh_token 있으면 Apple revoke 자동 호출. 응답 형식 변경 없음 |
| 신규 에러 | `502 A007` — 애플 인증 코드 교환 중 오류 (회원가입 시) |

상세 스펙은 `docs/spec/api-spec.md` 참고.

---

## 프론트 작업 지시

### 1. authorizationCode 추출

`sign_in_with_apple` 패키지(이미 사용 중일 가능성 높음) 사용 시:

```dart
final credential = await SignInWithApple.getAppleIDCredential(
  scopes: [
    AppleIDAuthorizationScopes.email,
    AppleIDAuthorizationScopes.fullName,
  ],
);

final identityToken = credential.identityToken;          // 기존
final authorizationCode = credential.authorizationCode;  // 신규 — String, 항상 제공됨
```

`SignInWithAppleAuthorizationCredential`은 `identityToken`과 `authorizationCode` 두 필드를 모두 제공한다. `authorizationCode`는 1회용이므로 **로그인 화면에서 받은 값을 회원가입 화면까지 state로 전달**해야 한다 (현재 `appleId`, `email`을 전달하는 방식과 동일).

### 2. API 요청 매핑

#### `POST /auth/apple` (Apple 로그인)

**Before**
```json
{
  "identityToken": "..."
}
```

**After**
```json
{
  "identityToken": "...",
  "authorizationCode": "..."
}
```

- `authorizationCode`는 옵셔널이지만 **가능하면 항상 포함**한다 (기존 Apple 사용자 backfill 유도)
- 누락해도 로그인은 정상 진행됨

#### `POST /auth/apple-signup` (Apple 회원가입)

**Before**
```json
{
  "identityToken": "...",
  "appleId": "001234.abcdef.5678",
  "nickname": "내닉네임",
  "termsAgreed": true
}
```

**After**
```json
{
  "identityToken": "...",
  "appleId": "001234.abcdef.5678",
  "nickname": "내닉네임",
  "termsAgreed": true,
  "authorizationCode": "..."
}
```

- `authorizationCode`는 **필수**. 누락 시 백엔드가 회원가입 차단
- 로그인 화면(`/auth/apple` 호출 시)에서 받은 `authorizationCode`를 회원가입 화면까지 state로 전달

### 3. 에러 처리 추가

| HTTP | 코드 | 메시지 | 처리 |
|------|------|--------|------|
| 502 | A007 | 애플 인증 코드 교환 중 오류가 발생했습니다. | 회원가입 화면에서 발생. "다시 시도" 안내 + 처음부터 Apple 로그인 재시도하도록 유도 (authorizationCode가 만료/소진됐을 가능성) |

기존 에러(`A005`, `A006`, `U004`, `U005`, `U007`, `U009`, `U006`)는 처리 변경 없음.

---

## 수정 대상 파일 (Flutter)

추정 경로 — 실제 구조에 맞게 조정 필요:

| 파일 | 변경 |
|------|------|
| `lib/models/auth/apple_login_request.dart` | `authorizationCode` 필드 추가 (옵셔널, nullable) + JSON 직렬화 |
| `lib/models/auth/apple_signup_request.dart` | `authorizationCode` 필드 추가 (**필수**, non-null) + JSON 직렬화 |
| `lib/services/auth_service.dart` 또는 Apple 로그인 호출부 | `credential.authorizationCode`를 request에 전달 |
| `lib/features/auth/` 하위 Apple 로그인/회원가입 화면 | 로그인 응답이 신규 유저면 `authorizationCode`를 회원가입 화면 state(provider/router extra/argument)로 전달 |

---

## 검증

### 신규 Apple 회원가입 플로우
1. Apple 로그인 → `isNewUser=true` 응답
2. 약관 + 닉네임 입력 화면 진입 (이때 `authorizationCode`를 state로 보유)
3. `/auth/apple-signup` 호출 시 `authorizationCode` 포함
4. 백엔드 DB `users.apple_refresh_token` 컬럼에 값 저장 확인 (백엔드 담당)

### 기존 Apple 사용자 backfill
1. 기존 Apple 사용자가 재로그인
2. `/auth/apple` 요청에 `authorizationCode` 포함
3. 백엔드 DB `users.apple_refresh_token`에 값이 채워졌는지 확인 (백엔드 담당)

### 회원탈퇴
1. Apple 사용자가 탈퇴 → 클라이언트는 별도 변경 불필요
2. 백엔드가 Apple `/auth/revoke` 호출 (서버 로그로 확인)
3. 동일 Apple 계정으로 재로그인 → 신규 회원가입 플로우로 진입하면 정상

### 에러 케이스
- `A007` 발생 시 사용자 안내 문구 적절한지 검증
- `authorizationCode`를 회원가입 화면까지 전달 못하는 경로(앱 백그라운드 진입 등)가 없는지 점검

---

## 주의사항

- `authorizationCode`는 **1회용**이다. 한 번 백엔드로 전송하여 `/auth/token` 교환이 끝나면 재사용 불가. 회원가입에 실패하면 사용자가 처음부터 Apple 로그인을 다시 해야 한다.
- 로그인 → 회원가입 화면 사이에 앱이 백그라운드 진입했다가 복귀하는 경우에도 state가 유지되어야 한다 (`authorizationCode` 분실 방지).
- iOS 시뮬레이터와 실기기 모두에서 `authorizationCode`가 정상 전달되는지 테스트 필요.
