# AI 에이전트 Handoff Document

> **작성일:** 2026-03-02
> **브랜치:** `feat/upload-sessions-happy-path`
> **Base URL:** `http://localhost:8080`

---

## 1. 프로젝트 개요 및 핵심 컨셉

**TriAgain — "작심삼일도 괜찮아. Start Small. Try Again."**

기존 습관 앱들은 연속 기록(스트릭) 기반이라, 한 번 실패하면 동기를 잃고 이탈하는 구조적 문제가 있다. TriAgain은 **실패를 허용하는 습관 형성 서비스**로, 이 문제를 해결한다.

- **3일 단위 챌린지 사이클**: 실패해도 즉시 새 사이클 시작 → 부담 없는 재도전
- **소규모 크루(2~10명)**: 함께 인증하고 응원하며 습관 형성
- **크루장이 인증 방식 선택**: TEXT(텍스트 필수) / PHOTO(사진 필수 + 텍스트 선택)

### Phase 1 목표

| 항목 | 목표 |
|------|------|
| 대상 유저 | 500명 |
| 목표 TPS | 50 |
| 핵심 기능 | 크루 생성/참여, 챌린지 사이클, 인증(텍스트/사진), 피드 조회 |

---

## 2. 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.13 |
| ORM | Spring Data JPA (CRUD/쓰기) + MyBatis 3.0.5 (복잡한 조회) |
| Database | PostgreSQL 16 |
| Storage | AWS S3 (Pre-signed URL 기반 Direct Upload) |
| Serverless | AWS Lambda (S3 업로드 완료 감지 → session COMPLETED 처리) |
| 실시간 통신 | SSE (Server-Sent Events, 60초 타임아웃) |
| Auth | 카카오 OAuth + JWT (access 30min / refresh 14d) |
| Client | Flutter 3.16.0 (iOS + Android) |
| Infra | AWS (EC2 + RDS), GitHub Actions CI/CD |
| Test | Cucumber 7.17.0 (BDD), REST-Assured 5.5.0, Testcontainers 1.21.3 |
| JWT | jjwt 0.12.6 (HMAC-SHA) |
| AWS SDK | v2 (BOM 2.31.3) |

### Phase 2 확장 예정

Redis (ElastiCache), AWS SQS, FCM 푸시

---

## 3. 아키텍처 구조

### 3.1 Bounded Context (5개)

```
User Context       — 회원/인증 (카카오 OAuth, JWT)
Crew Context       — 크루, 멤버, 챌린지 핵심 로직 (Core)
Verification Context — 인증, 업로드 세션, 피드
Moderation Context — 신고, 검토
Support Context    — 알림, 반응(이모지)
```

### 3.2 컨텍스트 간 통신

| From → To | 방식 | Port |
|-----------|------|------|
| Crew → User | 동기 호출 | `UserPort` → `UserClientAdapter` |
| Verification → Crew | 동기 호출 | `ChallengePort`, `CrewPort` → `ChallengeClientAdapter`, `CrewMembershipAdapter` |
| Moderation → Verification | 동기 호출 | `VerificationPort` → `VerificationClientAdapter` |
| Moderation → Crew | 동기 호출 | `CrewPort` → `CrewClientAdapter` |

현재 모든 컨텍스트 간 통신은 **같은 프로세스 내 Port/Adapter 동기 호출**이다 (HTTP 아님). Phase 2에서 비동기 이벤트 도입 예정.

### 3.3 헥사고날 아키텍처 — 패키지 구조

```
com.triagain.{context}/
├── api/               Controller, Request/Response DTO
│   └── internal/      Lambda 전용 Internal Controller
├── application/       UseCase 구현체 (Service)
├── domain/
│   ├── model/         Entity, Aggregate Root (POJO)
│   └── vo/            Value Object (Enum 등)
├── port/
│   ├── in/            UseCase 인터페이스
│   └── out/           Repository Port, External Port
└── infra/             JPA Entity, Adapter, MyBatis Mapper
```

### 3.4 계층별 의존성 규칙

- **Controller** → UseCase 인터페이스에만 의존, 비즈니스 로직 금지
- **Service** → Output Port 인터페이스에만 의존, `@Transactional` (쓰기)
- **Domain** → 외부 의존 없는 순수 POJO, Aggregate 간 참조는 ID로만
- **Adapter** → Output Port 구현, JPA Entity ↔ Domain Model 변환 담당

---

## 4. 핵심 비즈니스 규칙

### 4.1 크루 생성/참여

- **정원:** 2~10명, 비관적 락(`SELECT FOR UPDATE`)으로 동시 참여 시 정원 초과 방지
- **초대코드:** 6자리 영숫자 자동 발급 (0/O/I/L 제외)
- **시작일:** 내일(+1) 이후만 선택 가능
- **종료일:** 시작일+6일 이상 (작심삼일 2회 보장)
- **중간 가입:** 크루장이 `allowLateJoin` 설정 — true면 크루 시작 후에도 참여 가능
- **크루 생성자:** LEADER 역할로 자동 가입

### 4.2 챌린지 라이프사이클

```
크루 활성화(ACTIVE) → 멤버별 첫 챌린지 자동 생성
   ↓
3일 연속 인증 성공 → SUCCESS → 새 챌린지 자동 시작
인증 실패(마감 미인증) → FAILED → 새 챌린지 자동 시작
크루 기간 종료 → ENDED (진행 중 챌린지 일괄 종료)
```

- **사이클:** 3일 단위, 개인별 독립 진행
- **스케줄러:** 매일 03:00 크루 만료 확인 / 매 5분 챌린지 마감 확인

### 4.3 인증 규칙

- **횟수:** 하루 1회 (UNIQUE 제약: `user_id + crew_id + target_date`)
- **마감:** 크루의 `deadlineTime` 기준 (미지정 시 23:59:59)
- **텍스트 인증:** `POST /verifications` (텍스트 포함) → 바로 완료
- **사진 인증:** 업로드 세션 → S3 업로드 → 인증 생성 (아래 플로우 참조)
- **시간 판정:** `upload_session.requested_at` 기준 (서버 기록, 조작 불가)

### 4.4 사진 업로드 플로우

```
Client              Backend              S3              Lambda
  │                    │                  │                  │
  │ 1. POST /upload-sessions             │                  │
  │───────────────────►│ PENDING 생성     │                  │
  │◄───────────────────│ presignedUrl     │                  │
  │                    │                  │                  │
  │ 2. GET /upload-sessions/{id}/events (SSE 구독)          │
  │───────────────────►│                  │                  │
  │                    │                  │                  │
  │ 3. PUT presignedUrl (S3 직접 업로드)  │                  │
  │──────────────────────────────────────►│                  │
  │                    │                  │ 4. S3 Event      │
  │                    │                  │─────────────────►│
  │                    │                  │                  │
  │                    │ 5. PUT /internal/upload-sessions/{id}/complete
  │                    │◄──────────────────────────────────── │
  │                    │ → COMPLETED + SSE 발행              │
  │                    │                  │                  │
  │ 6. SSE: upload-complete              │                  │
  │◄───────────────────│                  │                  │
  │                    │                  │                  │
  │ 7. POST /verifications               │                  │
  │───────────────────►│ 인증 생성        │                  │
  │◄───────────────────│                  │                  │
```

**Upload Session 상태 흐름:**

```
PENDING → COMPLETED → USED (인증에 사용됨)
PENDING → EXPIRED (15분 초과 미사용)
```

### 4.5 S3 장애 Fallback 3단계

| Level | 상황 | upload_session | verification |
|-------|------|---------------|-------------|
| Level 1 | S3 일시 오류 | PENDING → COMPLETED | 생성 (클라이언트 자동 재시도) |
| Level 2 | S3 장애 지속 | PENDING → COMPLETED | 유예시간(deadline+1h) 내 생성 |
| Level 3 | S3 심각한 장애 | PENDING → EXPIRED | 생성 안 됨 |

### 4.6 신고/검토 정책

- 동일 인증에 대해 1인 1신고, 3건 이상 시 자동 검토 트리거
- 검토 주체: Phase 1 AUTO → Phase 2 크루장 → Phase 3 AI
- 7일 미검토 시 자동 승인

---

## 5. 현재 구현 상태 — 컨텍스트별 요약표

### 5.1 구현 현황

| Context | 상태 | 구현 범위 |
|---------|------|-----------|
| **Common** | ✅ 완료 | Auth(JWT+카카오), SecurityConfig(prod/dev 분리), GlobalExceptionHandler(42 에러코드), ApiResponse, IdGenerator |
| **User** | ✅ 완료 | 카카오 OAuth 로그인, 토큰 갱신, 프로필 조회/수정 — Controller 2, Service 6, Port 2 |
| **Crew** | ✅ 완료 | 생성/참여/목록/상세/활성화 + 스케줄러 2개 — Controller 1, Service 6, Scheduler 2, Port 3 |
| **Verification** | ✅ 완료 | 업로드 세션(생성/완료/만료/SSE) + 인증 생성 + 피드 조회 — Controller 3(+Internal 1), Service 4, Scheduler 1, Port 7 |
| **Moderation** | ⚠️ 도메인만 | Domain Model(Report, Review, ReportPolicy) + VO + Infra Adapter만 구현. API/Service 미구현 |
| **Support** | ⚠️ 도메인만 | Domain Model(Notification, Reaction) + VO + Infra Adapter만 구현. API/Service 미구현 |

### 5.2 구현 완료 API (17개)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/health` | 헬스체크 |
| 2 | POST | `/auth/kakao` | 카카오 로그인 |
| 3 | POST | `/auth/refresh` | 토큰 갱신 |
| 4 | POST | `/crews` | 크루 생성 |
| 5 | POST | `/crews/join` | 크루 참여 (초대코드) |
| 6 | GET | `/crews` | 내 크루 목록 |
| 7 | GET | `/crews/{crewId}` | 크루 상세 (멤버 프로필 + 챌린지 진행도) |
| 8 | POST | `/upload-sessions` | 업로드 세션 생성 (Presigned URL 발급) |
| 9 | GET | `/upload-sessions/{id}/events` | SSE 구독 (업로드 완료 알림) |
| 10 | PUT | `/internal/upload-sessions/{id}/complete` | Lambda → 세션 완료 (VPC 내부) |
| 11 | POST | `/verifications` | 인증 생성 (텍스트/사진) |
| 12 | GET | `/crews/{crewId}/feed` | 크루 피드 조회 (페이지네이션) |
| 13 | POST | `/auth/test-login` | 테스트 로그인 — userId로 JWT 발급 (`!prod` 전용) |
| 14 | POST | `/auth/signup` | 회원가입 (카카오 인증 + 닉네임 + 약관) |
| 15 | POST | `/auth/logout` | 로그아웃 (Phase 1: no-op) |
| 16 | GET | `/users/me` | 내 프로필 조회 |
| 17 | PATCH | `/users/me/nickname` | 닉네임 변경 |

### 5.3 스케줄러 (3개)

| 스케줄러 | Context | 주기 | 동작 |
|----------|---------|------|------|
| `CompleteExpiredCrewsScheduler` | Crew | 매일 03:00 | ACTIVE 크루 중 endDate 지난 것 → COMPLETED, 잔여 챌린지 → ENDED |
| `FailExpiredChallengesScheduler` | Crew | 매 5분 | 마감 지난 IN_PROGRESS 챌린지(미인증) → FAILED, 다음 챌린지 자동 생성 |
| `ExpireUploadSessionScheduler` | Verification | 매 5분 | 15분 이상 PENDING인 세션 → EXPIRED |

### 5.4 테스트 현황

| 구분 | 파일 수 | 설명 |
|------|---------|------|
| **Cucumber Feature** | 11개 | health, crew-creation, crew-join, crew-activation, crew-detail, crew-list, crew-feed, challenge-auto-creation, upload-session, verification-creation, my-profile |
| **단위 테스트** | 16개 | JWT, 도메인 모델(Crew, Challenge, UploadSession, Verification, Report, ReportPolicy), 서비스(KakaoLogin, RefreshToken, CompleteUploadSession, CreateVerification), 스케줄러 2개, Adapter 2개 |
| **인프라** | Testcontainers | PostgreSQL 컨테이너 기반 acceptance 테스트 + H2 인메모리 단위 테스트 |

---

## 6. 진행중/예정 작업

### 6.1 현재 브랜치

`feat/upload-sessions-happy-path` — Upload Session 해피패스 구현 완료, main 머지 전

### 6.2 후속 TODO 문서 (3건)

#### `/docs/archive/20260301-upload-session-review-todo.md`

PR 리뷰 후속 개선 항목 4건:
1. **[Medium]** `CompleteUploadSessionService`의 `afterCommit` 단위 테스트 추가
2. **[Medium]** `ChallengeClientAdapter.recordCompletion()` 예외 테스트 추가
3. **[Medium]** `SseEmitterAdapter` 엣지 케이스 테스트 (IOException, 타임아웃)
4. **[Low]** `CreateVerificationService` 챌린지 중복 조회 구조 리팩토링

#### `/docs/prod-deploy-checklist.md`

운영 배포 전 확인사항:
- 필수 환경변수 4개: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`
- AWS S3 버킷/CORS 설정
- `/internal/**` VPC 접근 제어
- DB 마이그레이션 도구 도입 (Flyway/Liquibase)
- SSL/HTTPS, Health check

### 6.3 Phase 1 남은 작업

| 작업 | 상태 |
|------|------|
| Moderation API/Service (신고 접수 → 자동 검토) | 미구현 |
| Support API/Service (반응/이모지) | 미구현 |
| 크루 탈퇴 API | 미구현 |
| Apple 로그인 (앱스토어 필수 요건) | 플랜 작성 중 |
| 프로덕션 배포 파이프라인 (GitHub Actions → EC2 + RDS) | 미구현 |
| `/internal/**` VPC 네트워크 접근 제어 | 미구현 |

### 6.4 Phase 2 예정

Redis 캐시, AWS SQS 비동기 이벤트, FCM 푸시 알림, 분산 락(Redis), JPA vs MyBatis 성능 비교, 공개 크루 탐색

---

## 7. 코드 컨벤션 및 규칙

### 7.1 핵심 규칙

| 항목 | 규칙 |
|------|------|
| DI | `@RequiredArgsConstructor` + `private final` (생성자 주입만) |
| DTO | Java `record` 사용, Entity 직접 반환 금지 |
| 예외 | `BusinessException(ErrorCode)` 사용, `RuntimeException` 금지 |
| 테스트 | BDD(Given-When-Then) 주석, 성공+실패 케이스 필수 |
| 주석 | public 메서드에 한 줄 한국어 Javadoc (`/** 무엇 — 언제/왜 */`) |
| ID 생성 | `IdGenerator.generate("PREFIX")` → `PREFIX-<16 hex>` |
| 응답 | 모든 API는 `ApiResponse<T>` 래핑 |
| Lombok | `@Data` 금지, `@RequiredArgsConstructor` + `@Getter` 허용 |

### 7.2 커밋 메시지 (AngularJS Convention)

```
<type>: <한국어 메시지>
- 부연 설명 (선택)
```

타입: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

### 7.3 Anti-Patterns (금지 사항)

- `@Autowired` 필드 주입 금지
- Controller에 비즈니스 로직 금지
- Domain에서 JPA/HTTP 의존 금지
- Port 없이 Adapter 직접 참조 금지
- 트랜잭션 안에 외부 API 호출 금지
- `@Data` 사용 금지
- 메서드 20라인 초과 시 분리 고려

---

## 8. 주의사항

### 8.1 아키텍처 함정

| 항목 | 설명 |
|------|------|
| **트랜잭션 + 외부 API** | 트랜잭션 안에서 S3 등 외부 API 호출 금지. SSE 발행도 `afterCommit`에서 수행 |
| **Presigned URL** | Presigned URL 생성은 S3 통신이 아님 (로컬 서명 생성). 트랜잭션 내부에서 호출해도 무방 |
| **session COMPLETED** | Lambda → `/internal/upload-sessions/{id}/complete`에서 처리. 트랜잭션 분리됨 |
| **SSE 타임아웃** | 60초. 클라이언트는 타임아웃 시 `POST /verifications` 시도로 상태 확인 (폴링 fallback) |
| **로그인 시 닉네임** | 카카오 재로그인 시 닉네임은 동기화하지 않음. email/profileImageUrl만 변경 시 조건부 save |

### 8.2 보안 주의

| 항목 | 설명 |
|------|------|
| **X-User-Id 헤더** | `!prod` 프로필에서만 허용 (`DevSecurityConfig`). 운영에서는 JWT만 유효 |
| **`/auth/test-login`** | `@Profile("!prod")` — prod에서는 빈 자체가 로드되지 않음. 카카오 로그인 없이 JWT 발급 |
| **`/internal/**` 경로** | Spring Security `permitAll()`이지만 **VPC Security Group으로 Lambda만 접근 허용** 필수 |
| **JWT Secret** | 운영에서 `${JWT_SECRET}` 환경변수 필수. 하드코딩 기본값 절대 금지 |
| **프로필 분리** | `LocalStorageAdapter`(!prod) / `S3StorageAdapter`(prod) — 프로필에 따라 자동 전환 |

### 8.3 문서 우선 참조

코드를 수정하기 전에 반드시 아래 문서를 먼저 확인할 것:

| 문서 | 경로 | 설명 |
|------|------|------|
| ERD | `/docs/spec/schema.md` | 엔티티 관계, 상태 Enum 정의, 인덱스 설계 |
| API 명세 | `/docs/spec/api-spec.md` | API 계약서 (요청/응답/에러 코드) |
| 비즈니스 규칙 | `/docs/spec/biz-logic.md` | 엣지케이스, Fallback 등급, Phase 로드맵 |
| 컨텍스트 맵 | `/docs/spec/context-map.md` | 바운디드 컨텍스트 관계도 |
| 아키텍처 | `/docs/spec/architecture.md` | 헥사고날 아키텍처 상세 |
| 배포 체크리스트 | `/docs/prod-deploy-checklist.md` | 프로필별 설정 비교, 필수 환경변수 |
| 리뷰 TODO | `/docs/archive/20260301-upload-session-review-todo.md` | Upload Session PR 후속 개선 항목 |
| 코딩 규칙 | `/CLAUDE.md` | 전체 코딩 컨벤션, Anti-Patterns, 디버깅 로그 규칙 |

### 8.4 디버깅 로그

버그 수정, 설계 결정, AI 방향 수정 시 `/docs/log/debugging-log.md`에 기록 필수. 형식:

```
### [날짜] 제목
- 상황: (한 줄)
- 내 판단: (결정 + 이유)
- AI 역할: (AI가 도운 것)
- 배운 점: (한 줄)
```
