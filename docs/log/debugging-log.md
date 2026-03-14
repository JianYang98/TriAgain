# 디버깅 & AI 협업 로그

> 기록 기준: 버그 수정 / 설계 판단 / AI 방향 수정 시에만 기록한다.
> 형식은 CLAUDE.md의 "디버깅 & AI 협업 로그 기록 규칙"을 따른다.

---

### [2026-03-14] crews 테이블 verificationContent 필드 추가 — 기존 데이터 백필 정책

- 상황: verification_content 컬럼(NOT NULL) 추가 시 기존 rows가 제약 위반
- 내 판단: 기존 데이터의 goal 컬럼에서 첫 50자를 백필 (V9: `UPDATE crews SET verification_content = SUBSTRING(goal, 1, 50) WHERE verification_content IS NULL`)
- AI 역할: 문서 동기화 리뷰에서 백필 정책 미기록 발견
- 배운 점: 기존 데이터가 있는 테이블에 NOT NULL 컬럼 추가 시 백필 정책을 디버깅 로그에 반드시 기록

---

### [2026-03-08] 기존 DB에 Flyway 도입 시 baseline 설정 필요

- 상황: Flyway 의존성 추가 후 EC2 배포 시 `Found non-empty schema but no schema history table` 에러. baseline 추가 후에도 V2부터 재실행하며 `column already exists` 에러
- 내 판단: `baseline-on-migrate: true` + `baseline-version: 6` (최신 마이그레이션 버전)으로 설정. 로컬은 `ddl-auto: update`라 Flyway 불필요 → `application-local.yml`에서 `flyway.enabled: false`
- AI 역할: baseline-on-migrate 설정 제안, baseline-version을 최신으로 올려야 하는 이유 설명
- 배운 점: 기존 DB에 Flyway 도입 시 baseline-version을 현재 스키마에 맞춰야 함. 이미 생성된 `flyway_schema_history`가 있으면 DROP 후 재시작 필요 (advisory lock 주의 — 앱 kill 먼저)

---

### [2026-03-08] Swagger UI `/swagger-ui.html` 경로가 permitAll 패턴에 매칭 안 됨

- 상황: SecurityConfig에 `/swagger-ui/**`, `/v3/api-docs/**` permitAll 추가했는데 `swagger-ui.html` 접속 시 401 에러
- 내 판단: `/swagger-ui.html`은 `/swagger-ui/index.html`로 리다이렉트하는 별도 경로라 `/swagger-ui/**` 패턴에 매칭 안 됨. `/swagger-ui.html`도 명시적으로 추가
- AI 역할: 리다이렉트 경로와 glob 패턴 불일치 원인 분석
- 배운 점: springdoc-openapi의 진입점은 `/swagger-ui.html`(리다이렉트)과 `/swagger-ui/**`(실제 리소스) 두 가지. 둘 다 permitAll 필요

---

### [2026-03-05] Riverpod CircularDependencyError — ApiClient → crewListProvider

- 상황: 로그아웃 시 401 인터셉터에서 `_ref.invalidate(crewListProvider)` 호출 → `CircularDependencyError` 발생
- 내 판단: ApiClient(apiClientProvider) → CrewService(crewServiceProvider) → crewListProvider 순환 참조. ApiClient에서 crewListProvider를 직접 invalidate하면 안 됨
- 해결: crewListProvider가 authTokenProvider를 watch하도록 변경 → 토큰 null 시 자동으로 빈 리스트 반환. ApiClient에서 crewListProvider invalidate + import 제거
- 배운 점: Riverpod에서 하위 레이어(네트워크)가 상위 레이어(비즈니스 provider)를 직접 조작하면 순환 참조 발생. 상태 변경의 전파는 watch 기반 반응형으로 처리하는 게 안전

---

### [2026-03-04] 서버 시작 시 밀린 스케줄러 보정 (StartupCompensationRunner)

- 상황: 로컬에서 PC 꺼져있는 동안 크루 시작일 지났는데 RECRUITING 상태로 남아있음 발견
- 내 판단: 서버 시작 시 보정이 필요하다고 판단 — ApplicationReadyEvent에서 3단계 보정 (활성화 → 실패 → 종료) 순서 보장, 각 step 독립 try-catch로 하나 실패해도 나머지 진행
- AI 역할: 오버엔지니어링 가능성 언급했으나, 내가 필요성을 주장하여 구현 진행. 구현 + 단위 테스트 5개 작성
- 배운 점: 단일 서버 + @Scheduled 조합은 서버 다운 시 작업 누락이 불가피하므로 시작 시 보정이 필수

---

### [2026-03-04] 크루 종료 스케줄러 실행 시각 — 00:00 대신 00:05

- 상황: 종료일 당일까지 인증 가능한데, 자정 정각(00:00)에 스케줄러 돌리면 날짜 경계 오차로 종료일 인증이 잘릴 위험
- 내 판단: 00:05에 실행하여 자정 경계 오차 방지 — POST /verifications의 today <= end_date가 1차 방어, 스케줄러는 2차 안전장치
- AI 역할: 타임라인 예시(deadline 22:00 → grace 22:05 → FAILED → 다음날 00:05 COMPLETED) 정리
- 배운 점: 날짜 경계에서 동작하는 스케줄러는 정각을 피하고 여유 마진을 두는 게 안전하다

---

### [2026-03-04] TEXT 인증 grace period 누락 발견 → 마감 기준 통일

- 상황: PHOTO 인증만 grace period 5분 적용, TEXT 인증은 deadline_time 정각 마감 — 인증 타입별 마감이 달라 혼란
- 내 판단: TEXT/PHOTO 모두 동일하게 deadline_time + gracePeriod(5분) 적용, 인증 API와 스케줄러가 같은 마감 기준 사용
- AI 역할: 기존 코드에서 TEXT grace period 미적용 발견, 통일 방안 설계
- 배운 점: 마감 기준이 어긋나면 "인증은 됐는데 FAILED 처리됨" 버그가 발생하므로 단일 기준으로 통일 필수

---

### [2026-03-03] 챌린지 생성 방식 Eager → Lazy 변경

- 상황: 크루 가입/활성화 시 챌린지를 즉시 생성(Eager)하면, 중간 가입자 처리가 복잡하고 실패 후 스케줄러가 새 사이클까지 생성해야 했음
- 내 판단: 첫 인증 시 챌린지 자동 생성(Lazy)으로 변경 — FindOrCreateActiveChallengeService 도입, 스케줄러는 FAILED만 처리, 동시성은 비관적 락 + Partial Unique Index 3중 방어
- AI 역할: Lazy 생성 패턴 설계, 동시성 제어 전략(SELECT FOR UPDATE + partial unique index + catch-retry), Grace Period 5분 적용 범위 정리
- 배운 점: 생성 책임을 사용 시점으로 미루면 중간 가입·재도전 플로우가 단순해지고, 불필요한 챌린지 생성을 방지할 수 있다

---

### [2026-03-03] DataIntegrityViolation 기본 에러코드 V003 하드코딩 버그

- 상황: 카카오 로그인(이메일 미동의) 시 email NOT NULL 위반 → V003 "이미 해당 날짜에 인증이 존재합니다" 반환 (엉뚱한 에러)
- 내 판단: 기본 fallback을 범용 DATA_CONFLICT(C004)로 변경 + constraint name 기반 분기 추가 + 글로벌 예외 핸들러에 request URI 로깅 추가
- AI 역할: GlobalExceptionHandler 분석 → 기본값 하드코딩 원인 특정, 로깅 개선 제안
- 배운 점: 예외 핸들러의 기본 fallback은 범용 코드로 두고, 구체적 매핑은 명시적 분기로 처리해야 한다

---

### [2026-03-01] Upload Session PR 리뷰 CRITICAL 3건 검증 + TODO 문서 생성

- 상황: PR 리뷰 CRITICAL 3건(예외 처리, 트랜잭션-SSE 분리, SseEmitter 추상화)이 이미 구현되었는지 검증 필요
- 내 판단: 플랜모드로 현재 상태 파악 → 검증 + TODO 문서화로 마무리 (코드가 이미 구현된 상태라 수정보다 확인이 우선이라서)
- AI 역할: 코드 확인으로 3건 구현 완료 검증, 테스트 실행, TODO 문서 생성
- 배운 점: PR 리뷰 피드백은 코드 확인 → 테스트 검증 → TODO 문서화까지 한 사이클로 처리하면 누락 없음