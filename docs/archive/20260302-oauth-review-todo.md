# 카카오 OAuth 코드 리뷰 — Phase 2 TODO

> 2026-03-02 코드 리뷰에서 발견된 이슈 중 Phase 1 기준 오버엔지니어링으로 미룬 항목

## 1. Refresh Token DB 저장소 추가 [CRITICAL → Phase 2]

- `refresh_tokens` 테이블 추가 (user_id, token_hash, expires_at, created_at)
- RT Rotation: 갱신 시 기존 RT 무효화 + 새 RT 발급
- 로그아웃 시 RT 무효화 (DELETE)
- 탈취 감지: 이미 사용된 RT로 갱신 시도 시 해당 유저의 모든 RT 무효화

## 2. 카카오 ID 충돌 가능성 검토

- 현재: `kakao_` + kakaoId로 유저 식별
- 다른 OAuth provider(Google, Apple) 추가 시 prefix 충돌 가능
- 대안: `provider` + `provider_id` composite unique key 검토

## 3. staging 환경 XUserIdAuthenticationFilter 비활성화 검토

- 현재: `!prod` 프로파일에서 XUserIdAuthenticationFilter 활성화
- staging 환경에서도 JWT 인증만 사용해야 하는지 결정 필요
- 대안: `local`, `test` 프로파일에서만 활성화하도록 분리
