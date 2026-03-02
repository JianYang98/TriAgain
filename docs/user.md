# User Context — 인증 및 회원 설계

> 중앙 문서(`biz-logic.md`, `schema.md`)는 전체 오버뷰, 이 문서는 User Context 상세 설계.

---

## 1. 인증 흐름

### 1.1 전체 시퀀스

```
Flutter App                   Backend                     카카오 API
    │                            │                            │
    ├─ 카카오 SDK 로그인 ────────┤                            │
    │  (카카오 accessToken 획득)  │                            │
    │                            │                            │
    ├─ POST /auth/kakao ────────►│                            │
    │  { kakaoAccessToken }      │                            │
    │                            ├─ GET /v2/user/me ─────────►│
    │                            │  (토큰으로 사용자 정보 조회)  │
    │                            │◄── { id, nickname, email } ─┤
    │                            │                            │
    │                            ├─ DB 조회: users.id = 카카오 ID
    │                            │  ├─ 존재 → 로그인 (프로필 갱신)
    │                            │  └─ 미존재 → 자동 회원가입
    │                            │
    │◄── { accessToken, refreshToken, user } ─┤
    │                            │
    ├─ 이후 API 호출 ───────────►│
    │  Authorization: Bearer <accessToken>
    │                            ├─ JWT 검증 → userId 추출
    │                            │
```

### 1.2 핵심 포인트

- **클라이언트**: 카카오 SDK로 로그인 → 카카오 accessToken 획득 → 백엔드에 전달
- **백엔드**: 카카오 API로 사용자 정보 조회 → 회원가입/로그인 처리 → 자체 JWT 발급
- **카카오 accessToken**은 백엔드에서 사용자 정보 조회용으로만 사용, 저장하지 않음

---

## 2. 비즈니스 규칙

| 항목 | 내용 |
|------|------|
| 로그인 방식 | 카카오 소셜 로그인 (Phase 1 유일) |
| 회원가입 | 별도 절차 없음, 첫 로그인 시 자동 생성 |
| User ID | 카카오 고유 ID를 String으로 변환하여 `users.id`에 직접 사용 |
| ID 생성 | `IdGenerator.generate("USR")` 미사용 — 카카오 ID가 곧 PK |
| provider | `KAKAO` 고정 (향후 다른 소셜 로그인 확장 대비) |
| provider_id | 별도 컬럼 불필요 — `id` 자체가 카카오 ID |
| 저장 정보 | 닉네임, 프로필 이미지 URL (카카오에서 가져옴) |
| 이메일 | 카카오에서 제공 시 저장, 미동의 시 null 허용 |
| 프로필 갱신 | 로그인할 때마다 카카오 최신 정보로 갱신 |
| 인증 토큰 | 자체 JWT (Access + Refresh) |

---

## 3. User ID 전략: 카카오 ID 직접 사용

### 3.1 결정

`users.id`에 카카오에서 제공하는 고유 ID(숫자)를 String으로 변환하여 그대로 사용한다.

### 3.2 이유

| 관점 | 설명 |
|------|------|
| 단순성 | 별도 매핑 테이블/컬럼 불필요 |
| 조회 효율 | 카카오 ID로 바로 PK 조회 가능 (추가 인덱스 불필요) |
| 멱등성 | 동일 카카오 유저가 재로그인해도 같은 ID로 자연스럽게 upsert |

### 3.3 주의사항

- 카카오 ID는 숫자이나, DB에는 String으로 저장 (향후 다른 소셜 로그인 ID 형식 대비)
- 기존 `IdGenerator.generate("USR")` 패턴은 User에서 더 이상 사용하지 않음
- 다른 도메인(Crew, Verification 등)은 기존 `IdGenerator` 패턴 유지

---

## 4. DB 스키마 변경

### 4.1 현재 → 변경 후

```
현재 (User.java / UserJpaEntity.java)
┌──────────────────────────────┐
│ users                        │
├──────────────────────────────┤
│ id         string PK         │ ← IdGenerator.generate("USR")
│ email      string NOT NULL UK│
│ nickname   string NOT NULL   │
│ profile_image_url string     │
│ created_at timestamp         │
└──────────────────────────────┘

변경 후
┌──────────────────────────────┐
│ users                        │
├──────────────────────────────┤
│ id         string PK         │ ← 카카오 고유 ID (String 변환)
│ provider   string NOT NULL   │ ← 'KAKAO' (신규 컬럼)
│ email      string (nullable) │ ← nullable로 변경, unique 제거
│ nickname   string NOT NULL   │
│ profile_image_url string     │
│ created_at timestamp         │
└──────────────────────────────┘
```

### 4.2 마이그레이션 SQL

```sql
-- 1. provider 컬럼 추가
ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'KAKAO';

-- 2. email nullable + unique 제거
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
```

### 4.3 변경 사유

| 변경 | 사유 |
|------|------|
| `id` ← 카카오 ID | 별도 매핑 불필요, 조회 효율 |
| `provider` 추가 | 향후 Apple/Google 로그인 확장 대비 |
| `email` nullable | 카카오 이메일 미동의 사용자 지원 |
| `email` unique 제거 | 소셜 로그인은 provider + id로 식별, 이메일은 보조 정보 |

---

## 5. API 명세

### 5.1 POST /auth/kakao — 카카오 로그인 / 자동 회원가입

**요청 (Request)**
```json
POST /auth/kakao HTTP/1.1
Content-Type: application/json

{
  "kakaoAccessToken": "카카오_SDK에서_받은_토큰"
}
```

**성공 응답 (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "accessTokenExpiresIn": 1800,
  "user": {
    "id": "1234567890",
    "nickname": "홍길동",
    "profileImageUrl": "https://k.kakaocdn.net/...",
    "isNewUser": true
  }
}
```

**필드 설명:**
- `accessToken`: API 호출용 JWT (30분 유효)
- `refreshToken`: 토큰 갱신용 JWT (14일 유효)
- `accessTokenExpiresIn`: Access Token 만료까지 남은 초
- `isNewUser`: 신규 회원가입 여부 (클라이언트 온보딩 분기용)

**실패 응답**
```json
// 401 Unauthorized - 카카오 토큰 무효
{
  "code": "INVALID_KAKAO_TOKEN",
  "message": "카카오 인증에 실패했습니다."
}

// 502 Bad Gateway - 카카오 API 장애
{
  "code": "KAKAO_API_ERROR",
  "message": "카카오 서버와 통신에 실패했습니다. 잠시 후 다시 시도해주세요."
}
```

### 5.2 POST /auth/refresh — 토큰 갱신

**요청 (Request)**
```json
POST /auth/refresh HTTP/1.1
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**성공 응답 (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "accessTokenExpiresIn": 1800
}
```

**실패 응답**
```json
// 401 Unauthorized - Refresh Token 만료/무효
{
  "code": "INVALID_REFRESH_TOKEN",
  "message": "인증이 만료되었습니다. 다시 로그인해주세요."
}
```

---

## 6. JWT 설계

### 6.1 토큰 스펙

| 항목 | Access Token | Refresh Token |
|------|-------------|---------------|
| 유효기간 | 30분 | 14일 |
| Payload | userId, provider | userId |
| 서명 | HS256 (단일 서버) | HS256 |
| 저장 위치 (클라이언트) | 메모리 / Secure Storage | Secure Storage |

### 6.2 Access Token Payload

```json
{
  "sub": "1234567890",
  "provider": "KAKAO",
  "iat": 1709100000,
  "exp": 1709101800
}
```

### 6.3 토큰 갱신 전략

```
Access Token 만료 → 401 응답
→ 클라이언트가 POST /auth/refresh (Refresh Token 전달)
→ 새 Access Token 발급
→ Refresh Token도 만료 시 → 재로그인 유도
```

- Refresh Token Rotation은 Phase 1에서 미적용 (단일 디바이스 가정)
- Phase 2에서 다중 디바이스 지원 시 Rotation 도입 검토

---

## 7. X-User-Id → Authorization: Bearer 전환 계획

### 7.1 현재 상태

- 모든 Controller가 `@RequestHeader("X-User-Id") String userId`로 사용자 식별
- Spring Security 미설정, JWT 미도입
- Cucumber 테스트에서 `.header("X-User-Id", userId)` 사용

### 7.2 전환 단계

| 단계 | 작업 | 영향 범위 |
|------|------|----------|
| 1 | JWT 발급/검증 구현 (`JwtProvider`) | 신규 클래스 |
| 2 | Spring Security Filter 추가 (`JwtAuthenticationFilter`) | 신규 클래스 |
| 3 | SecurityContext에서 userId 추출하는 `@AuthenticatedUser` 어노테이션 또는 `ArgumentResolver` 구현 | 신규 클래스 |
| 4 | Controller의 `@RequestHeader("X-User-Id")` → `@AuthenticatedUser` 일괄 전환 | 전체 Controller |
| 5 | Cucumber 테스트 헬퍼: 테스트용 JWT 발급 → `.header("Authorization", "Bearer " + token)` 전환 | TestAdapter 변경 |

### 7.3 .feature 파일 영향

- `"사용자 XXX가 로그인되어 있다"` 같은 추상적 표현 → **변경 불필요**
- Step Definition 내부에서 JWT 발급 로직만 변경

---

## 8. 엣지케이스

### 8.1 이메일 미동의

| 상황 | 처리 |
|------|------|
| 카카오 이메일 동의 안 함 | `email = null`로 저장, 서비스 이용에 영향 없음 |
| 나중에 이메일 동의 | 다음 로그인 시 프로필 갱신으로 이메일 저장 |

### 8.2 카카오 토큰 만료/무효

| 상황 | 처리 |
|------|------|
| 만료된 카카오 토큰 전달 | 401 INVALID_KAKAO_TOKEN |
| 위조된 토큰 전달 | 401 INVALID_KAKAO_TOKEN |

### 8.3 동일 사용자 중복 요청

| 상황 | 처리 |
|------|------|
| 이미 가입된 카카오 ID로 로그인 | 기존 유저 조회 → 프로필 갱신 → JWT 발급 (정상 로그인) |
| 동시 로그인 요청 | ID 기반 upsert이므로 충돌 없음 |

### 8.4 카카오 API 장애

| 상황 | 처리 |
|------|------|
| 카카오 API 타임아웃 | 502 KAKAO_API_ERROR, 클라이언트 재시도 유도 |
| 카카오 API 5xx | 502 KAKAO_API_ERROR |

### 8.5 JWT 관련

| 상황 | 처리 |
|------|------|
| Access Token 만료 | 401 → 클라이언트가 /auth/refresh 호출 |
| Refresh Token 만료 | 401 INVALID_REFRESH_TOKEN → 재로그인 |
| 위조된 JWT | 401 UNAUTHORIZED |

---

## 9. 도메인 모델 변경 요약

### 9.1 User.java 변경사항

```java
// 현재
public static User create(String email, String nickname) {
    return new User(IdGenerator.generate("USR"), email, nickname, null, LocalDateTime.now());
}

// 변경 후
public static User createFromKakao(String kakaoId, String nickname, String email, String profileImageUrl) {
    return new User(kakaoId, "KAKAO", email, nickname, profileImageUrl, LocalDateTime.now());
}
```

- `provider` 필드 추가
- `email` 필수 검증 제거 (nullable)
- `create()` → `createFromKakao()`로 팩토리 메서드 변경

### 9.2 UserJpaEntity.java 변경사항

- `provider` 컬럼 추가 (`@Column(nullable = false)`)
- `email` → `@Column(nullable = false, unique = true)` 에서 `@Column` (nullable, unique 제거)

---

## 10. TODO 구현 체크리스트

### DB 변경
- [ ] Flyway 마이그레이션: `provider` 컬럼 추가
- [ ] Flyway 마이그레이션: `email` nullable 변경, unique 제거

### 도메인 모델
- [ ] `User.java`: `provider` 필드 추가, `createFromKakao()` 팩토리 메서드
- [ ] `UserJpaEntity.java`: `provider` 컬럼, `email` nullable

### 인증 인프라
- [ ] `JwtProvider`: JWT 생성/검증 유틸리티
- [ ] `JwtAuthenticationFilter`: Spring Security Filter
- [ ] `KakaoApiClient`: 카카오 API 호출 (GET /v2/user/me)
- [ ] `KakaoApiClientAdapter`: `KakaoApiPort` 구현체

### API
- [ ] `AuthController`: POST /auth/kakao, POST /auth/refresh
- [ ] `KakaoLoginUseCase` / `KakaoLoginService`
- [ ] `RefreshTokenUseCase` / `RefreshTokenService`
- [ ] Refresh Token 저장소 (DB 또는 인메모리)

### 기존 코드 전환
- [ ] Controller: `@RequestHeader("X-User-Id")` → JWT 기반 인증으로 전환
- [ ] `@AuthenticatedUser` 어노테이션 또는 `ArgumentResolver` 구현
- [ ] Spring Security 설정 (`SecurityConfig`)

### 테스트
- [ ] `KakaoLoginService` 단위 테스트
- [ ] `JwtProvider` 단위 테스트
- [ ] Cucumber TestAdapter: JWT 기반 인증 헬퍼
- [ ] 카카오 로그인 통합 테스트 (MockServer 또는 WireMock)
