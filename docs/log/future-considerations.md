# 추후 고려 사항

> 개발 중 나온 개선 아이디어나 스케일업 시 필요한 작업을 기록한다.
> 지금은 불필요하지만, 나중에 참고할 내용들.
> **최신 항목이 위에 오도록 추가한다.**

---

### [2026-03-11] 썸네일 생성은 Phase 2로 보류

- 현재 상태: 클라이언트 압축 이미지 1장만 업로드. 썸네일 미생성. COMPLETED = "원본 1장 업로드 완료"
- 필요 시점: Phase 2 (피드 성능 최적화 시)
- 이유: 지금 도입하면 아래 결정이 추가로 필요하여 업로드 플로우 안정화가 지연됨
  - 썸네일 생성 완료까지를 업로드 완료로 볼지
  - thumbnailUrl 저장 위치 (upload_session? verification?)
  - 피드/상세 응답 분기 방법
  - 썸네일 생성 실패 시 fallback 처리
- Phase 2 확장 방향:
  - thumbnailUrl 필드 추가 (피드: 썸네일, 상세: 원본)
  - 현재 imageUrl 중심 구조에서 thumbnailUrl만 추가하면 큰 변경 없이 확장 가능

---

### [2026-03-10] ~~코드 버그: 크루 최소 기간 미검증 (Crew.validateDates)~~ → 해결 완료 (2026-03-12)

- ~~현재 상태: `Crew.validateDates()`에서 `endDate > startDate`만 체크. biz-logic.md의 "최소 시작일+6일 (작심삼일 2회 보장)" 규칙이 코드에 미반영~~
- **해결**: `Crew.validateDates()`에 `endDate.isBefore(startDate.plusDays(6))` 검증 추가. 단위테스트(경계값+실패) 포함.

---

### [2026-03-12] 크루 최소 인원: 백엔드 @Min(1) 유지, 프론트에서 @Min(2) 제한

- 현재 상태: CreateCrewRequest @Min(1), Crew.java maxMembers < 1. biz-logic.md 규칙은 "2~10명"
- 필요 시점: 프론트 크루 생성 UI 구현 시
- 이유: 백엔드는 솔로 테스트 및 향후 솔로 모드 확장을 위해 @Min(1) 유지. 프론트 UI에서 최소 2명 제한으로 정상 사용자 가드. API 직접 호출로 1명 크루 생성 가능하나 Phase 1 규모에서 실질적 위험 낮음

---

### [2026-03-04] StartupCompensationRunner — Phase 2 전환 시 제거 검토

- 현재 상태: 단일 서버 + Spring @Scheduled 기반이라 서버 다운 시 스케줄러 미실행 → 서버 재시작 시 밀린 작업(크루 활성화 → 챌린지 실패 → 크루 종료)을 순서대로 보정
- 필요 시점: Phase 2 (Quartz 등 persistent scheduler 도입 시)
- 이유: Quartz의 misfire policy가 자동 보정을 제공하므로 이 Runner 제거 가능. 단, 제거 전에 3단계 순서(활성화 → 실패 → 종료) 보장 여부 확인 필요

---

### [2026-03-04 21:50] 챌린지 Lazy 생성 — 실패 후 미재도전 유저 알림

- 현재 상태: 챌린지 생성을 Eager(크루 활성화/참여 시) → Lazy(첫 인증 시 자동 생성)로 변경. 스케줄러는 FAILED 처리만 수행, 새 챌린지 자동 생성 제거.
- 필요 시점: Phase 2 (알림 시스템 도입 시)
- 이유: Lazy 생성이므로 실패 후 재도전하지 않는 유저는 챌린지가 없는 상태로 남음. 리마인더 푸시("다시 도전해보세요!")가 필요하지만 Phase 1에서는 알림 시스템 미구현.

---

### [2026-03-04 20:10] Apple 로그인 실제 연동 TODO

- 현재 상태: 코드 구현 완료 (Port/Adapter/UseCase/Controller/테스트), Cucumber @ignore + AdapterTest @Disabled
- 필요 시점: 앱스토어 출시 전
- 남은 작업:
  - Apple Developer 계정에서 Service ID 발급 → APPLE_CLIENT_ID 환경변수 설정
  - 실제 Apple Identity Token으로 E2E 검증
  - Cucumber @ignore / AdapterTest @Disabled 해제
  - Flutter 클라이언트 Apple Sign In 연동
- 이유: 백엔드 코드는 준비 완료, Apple Developer 계정 설정 + 클라이언트 연동이 별도 작업

---

### [2026-03-03 18:00] Logout 토큰 블랙리스트 도입

- 현재 상태: `POST /auth/logout`은 서버 no-op (200 반환만), 클라이언트가 로컬 토큰 삭제로 로그아웃 처리. refreshToken은 순수 JWT stateless.
- 필요 시점: Phase 2 (Redis 도입 이후)
- 이유: Phase 1에서는 Redis 미사용, 토큰 탈취 시나리오 대응은 Phase 2 보안 강화 시점에 적합
- Phase 2 계획:
  - `token_blacklist` 테이블 또는 Redis SET으로 블랙리스트 관리
  - `TokenBlacklistPort` (Output Port) + `RedisTokenBlacklistAdapter` 구현
  - `RefreshTokenService.refresh()` 시 블랙리스트 조회 추가
  - 만료된 블랙리스트 항목 정리 스케줄러 추가
  - `LogoutUseCase` 생성하여 블랙리스트 등록 로직 분리

---

### [2026-03-03 11:14] 예외 핸들러 로그 폭주 대비

- 맥락: GlobalExceptionHandler에 전체 핸들러 request URI 로깅 추가
- 지금 한 것: 모든 예외 발생 시 `[POST /auth/signup]` 형태로 요청 정보 로깅
- 추후 고려: TPS가 올라가면 동일 에러가 초당 수백 건 반복될 수 있음 (예: 봇 공격)
  - Rate-limiting 로깅 또는 Sampling 적용 검토
  - Phase 1 (TPS 50)에선 해당 없음, Phase 2 이후 트래픽 증가 시 재검토

---

### [2026-03-03 11:12] `/internal/**` Lambda 인증 필터 추가 필요

- 맥락: `/internal/**` 엔드포인트가 `permitAll`로 열려있어 보안 위험 → prod에서 `denyAll`로 임시 차단
- 지금 한 것: `SecurityConfig`(prod)에서 `denyAll`로 변경, `DevSecurityConfig`(!prod)는 `permitAll` 유지
- 추후 고려: Lambda 연동 시 시크릿 키 헤더 검증 필터 추가
  - Lambda 요청에 `X-Internal-Secret` 등의 헤더를 포함하고, Spring Security 필터에서 검증
  - 필터 추가 후 dev/prod 설정 통일 (`denyAll` → 필터 기반 인증으로 전환)
  - VPC 내부 통신만 허용하는 네트워크 레벨 제한도 병행 검토