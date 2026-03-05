# AI 에이전트 핸드오프 — TriAgain 백엔드

> **작성일**: 2026-03-04
> **브랜치**: `feat/upload-sessions-happy-path`
> **대상**: 이 프로젝트를 이어받는 AI 에이전트

---

## 1. 프로젝트 개요

### 서비스 소개

**TriAgain (작심삼일 크루)** — "Start Small. Try Again."

기존 습관 형성 앱의 구조적 문제(스트릭 기반 → 한 번 실패 시 동기 상실)를 해결하는 서비스.
3일 단위 챌린지 사이클로, 실패해도 부담 없이 재도전할 수 있다.

### 핵심 비즈니스 규칙

- **3일 사이클**: 크루원은 연속 3일 인증해야 챌린지 성공. 하루라도 빠지면 FAILED.
- **FAILED 시 자동 재시작**: 새 챌린지 사이클이 자동 생성된다.
- **소규모 크루**: 2~10명. 크루장이 인증 방식 선택 (TEXT / PHOTO).
- **인증 마감**: 크루 생성 시 설정한 `deadlineTime` (기본 23:59:59) 이전에 인증 완료해야 한다.

### Phase 1 목표

- 대상 유저: 500명
- 목표 TPS: 50
- 핵심 기능: 크루 생성/참여, 챌린지 사이클, 인증(텍스트/사진)

---

## 2. 기술 스택 및 패키지

### Runtime & Framework

| 항목 | 버전 |
|------|------|
| Java | 17 |
| Spring Boot | 3.4.13 |
| PostgreSQL | 16 |

### 주요 Dependencies (build.gradle)

```
# Core
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-validation

# Auth
jjwt 0.12.6 (HMAC-SHA, accessToken 30분 / refreshToken 14일)

# DB
postgresql (runtime)
mybatis-spring-boot-starter 3.0.5 (복잡한 조회 쿼리)

# AWS
aws-java-sdk-bom 2.31.3
software.amazon.awssdk:s3 (Pre-signed URL)

# Testing
cucumber-java 7.17.0 (BDD 인수 테스트)
cucumber-spring
rest-assured 5.5.0
testcontainers 1.21.3 (PostgreSQL 컨테이너)
h2 (단위 테스트용 인메모리 DB)
spring-security-test

# Etc
lombok (최소 사용)
```

### ORM 전략

- **JPA**: 기본 CRUD, 엔티티 관리, 쓰기 작업
- **MyBatis**: 복잡한 조회 쿼리 (피드 페이지네이션 등)

---

## 3. 폴더 구조

```
triagain-back/
├── src/
│   ├── main/
│   │   ├── java/com/triagain/
│   │   │   ├── TriAgainApplication.java
│   │   │   ├── common/               # 공통 인프라
│   │   │   │   ├── auth/             # JWT, SecurityConfig, 필터
│   │   │   │   ├── config/           # MyBatis, S3, RestClient, WebMvc 설정
│   │   │   │   ├── exception/        # BusinessException, ErrorCode(42개), GlobalExceptionHandler
│   │   │   │   ├── response/         # ApiResponse<T>, ErrorResponse
│   │   │   │   └── util/             # IdGenerator (UUID 기반)
│   │   │   │
│   │   │   ├── user/                 # User Context
│   │   │   │   ├── api/              # AuthController, UserController, TestLoginController
│   │   │   │   ├── application/      # KakaoLoginService, AppleLoginService, SignupService 등
│   │   │   │   ├── domain/model/     # User (Aggregate Root)
│   │   │   │   ├── infra/            # UserJpaAdapter, KakaoApiAdapter, AppleTokenVerifierAdapter
│   │   │   │   └── port/             # in: UseCase 인터페이스 / out: Repository, API 포트
│   │   │   │
│   │   │   ├── crew/                 # Crew Context
│   │   │   │   ├── api/              # CrewController
│   │   │   │   ├── application/      # CreateCrewService, JoinCrewService, 스케줄러 2개
│   │   │   │   ├── domain/model/     # Crew, CrewMember, Challenge (Aggregate Root)
│   │   │   │   ├── domain/vo/        # CrewStatus, ChallengeStatus, VerificationType 등
│   │   │   │   ├── infra/            # CrewJpaAdapter, ChallengeJpaAdapter, UserClientAdapter
│   │   │   │   └── port/             # in: UseCase / out: Repository, UserPort
│   │   │   │
│   │   │   ├── verification/         # Verification Context
│   │   │   │   ├── api/              # UploadSessionController, VerificationController, FeedController
│   │   │   │   ├── api/internal/     # InternalUploadSessionController (Lambda 콜백)
│   │   │   │   ├── application/      # CreateUploadSessionService, CreateVerificationService 등
│   │   │   │   ├── domain/model/     # UploadSession, Verification (Aggregate Root)
│   │   │   │   ├── domain/vo/        # UploadSessionStatus, VerificationStatus 등
│   │   │   │   ├── infra/            # S3StorageAdapter, SseEmitterAdapter, JPA/MyBatis 어댑터
│   │   │   │   └── port/             # in: UseCase / out: Repository, StoragePort, SsePort 등
│   │   │   │
│   │   │   ├── moderation/           # Moderation Context (도메인만, API 미구현)
│   │   │   │   ├── domain/model/     # Report, ReportPolicy, Review
│   │   │   │   ├── domain/vo/        # ReportReason, ReportStatus, ReviewerType, ReviewDecision
│   │   │   │   ├── infra/            # JPA 어댑터, 컨텍스트 간 클라이언트 어댑터
│   │   │   │   └── port/out/         # ReportRepositoryPort, ReviewRepositoryPort 등
│   │   │   │
│   │   │   └── support/              # Support Context (도메인만, API 미구현)
│   │   │       ├── domain/model/     # Notification, Reaction
│   │   │       ├── domain/vo/        # NotificationType
│   │   │       ├── infra/            # JPA 어댑터
│   │   │       └── port/out/         # NotificationRepositoryPort, ReactionRepositoryPort
│   │   │
│   │   └── resources/
│   │       ├── application.yml       # 메인 설정 (local 프로파일)
│   │       ├── application-prod.yml  # 프로덕션 설정
│   │       ├── mybatis/              # MyBatis 매퍼 XML
│   │       └── db/migration/         # (Flyway 예정, 현재 미사용)
│   │
│   └── test/
│       ├── java/com/triagain/
│       │   ├── acceptance/           # Cucumber 인수 테스트
│       │   └── unit/                 # 단위 테스트
│       └── resources/features/       # Cucumber feature 파일 11개
│
├── docs/
│   ├── spec/
│   │   ├── api-spec.md              # API 명세 (정본)
│   │   ├── schema.md                # DB 스키마 (정본)
│   │   ├── biz-logic.md             # 비즈니스 규칙 (정본)
│   │   ├── architecture.md          # 헥사고날 아키텍처 상세
│   │   ├── context-map.md           # 바운디드 컨텍스트 관계도
│   │   └── sequence/                # 시퀀스 다이어그램
│   ├── log/
│   │   ├── debugging-log.md         # 버그/설계 판단 기록
│   │   └── future-considerations.md # Phase 2+ 개선 사항
│   ├── guide/                       # 개발 가이드
│   ├── archive/                     # PR 리뷰 TODO 등 보관
│   ├── handoff.md                   # 인수인계 문서
│   └── schedule.md                  # 개발 일정
│
├── CLAUDE.md                        # 에이전트 컨벤션 (필독)
└── build.gradle
```

---

## 4. 아키텍처 — 헥사고날 + DDD

### 핵심 원칙

```
Domain (순수 POJO)
  ↑
Port/in (UseCase 인터페이스)
  ↑
Application (UseCase 구현체, @Transactional)
  ↓
Port/out (Repository / External 포트 인터페이스)
  ↓
Infra (JPA Adapter, S3Adapter, KakaoApiAdapter 등)
```

- **Domain은 외부 의존 없음** — JPA, HTTP, S3 직접 의존 금지
- **컨텍스트 간 참조**: 직접 의존 금지, Port → Adapter 패턴으로 통신
- **Aggregate 간 참조**: ID로만 (직접 객체 참조 금지)

### 5개 바운디드 컨텍스트

| Context | 역할 | 구현 상태 |
|---------|------|-----------|
| **User** | 인증/프로필 | ✅ 완료 |
| **Crew** | 크루/챌린지 핵심 도메인 | ✅ 완료 |
| **Verification** | 인증/업로드 | ✅ 완료 (해피패스) |
| **Moderation** | 신고/검토 | ⚠️ 도메인만 (API 미구현) |
| **Support** | 알림/반응 | ⚠️ 도메인만 (API 미구현) |

---

## 5. 현재 구현된 기능 (API 19개)

### 인증 (Auth)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/auth/kakao` | 카카오 로그인 (신규: kakaoId+profile 반환, 기존: JWT 반환) |
| POST | `/auth/signup` | 카카오 회원가입 (닉네임 + 약관동의 + kakaoAccessToken) |
| POST | `/auth/apple` | Apple 로그인 |
| POST | `/auth/apple-signup` | Apple 회원가입 (닉네임 + 약관동의 + identityToken) |
| POST | `/auth/refresh` | 토큰 갱신 (refreshToken → 새 accessToken) |
| POST | `/auth/logout` | 로그아웃 (Phase 1: no-op, 클라이언트 토큰 삭제 방식) |
| POST | `/auth/test-login` | **dev/test 전용** — X-User-Id 헤더로 JWT 발급 |

### 사용자 (User)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/users/me` | 내 프로필 조회 |
| PATCH | `/users/me/nickname` | 닉네임 변경 (2~12자, 한글/영문/숫자/_) |

### 크루 (Crew)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/crews` | 크루 생성 (크루장 자동 등록, 초대코드 6자 자동 생성) |
| POST | `/crews/join` | 초대코드로 크루 참여 |
| GET | `/crews` | 내 크루 목록 (페이지네이션) |
| GET | `/crews/{crewId}` | 크루 상세 (멤버 목록 + 챌린지 진행 현황) |

### 인증/업로드 (Verification)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/upload-sessions` | 업로드 세션 생성 (S3 Pre-signed URL 발급, 15분 유효) |
| GET | `/upload-sessions/{id}/events` | SSE 구독 (업로드 완료 이벤트, 60초 타임아웃) |
| PUT | `/internal/upload-sessions/{id}/complete` | **Lambda 전용** — 세션 PENDING→COMPLETED + SSE 발행 |
| POST | `/verifications` | 인증 생성 (텍스트/사진) |
| GET | `/crews/{crewId}/feed` | 크루 피드 (인증 목록, MyBatis 페이지네이션) |

### 공통

| Method | Path | 설명 |
|--------|------|------|
| GET | `/health` | 헬스 체크 |

---

## 6. 상태 관리 방식

### 토큰 관리 (클라이언트 기준)

- `accessToken`: 메모리만 (30분 만료)
- `refreshToken`: SecureStorage 저장, 앱 시작 시 복원
- 401 응답 시 → refresh → 재시도 → 실패 시 로그아웃

### 핵심 상태 머신

**크루 (Crew)**: `RECRUITING` → `ACTIVE` → `COMPLETED`
- RECRUITING: 크루 생성~시작일 전
- ACTIVE: startDate 도래 시 스케줄러가 자동 전환
- COMPLETED: endDate 이후 스케줄러가 자동 종료

**챌린지 (Challenge)**: `IN_PROGRESS` → `SUCCESS` / `FAILED` / `ENDED`
- SUCCESS: 3일 연속 인증 완료
- FAILED: 마감 시간 내 미인증 → 다음 사이클 자동 생성
- ENDED: 크루 COMPLETED 시

**업로드 세션 (UploadSession)**: `PENDING` → `COMPLETED` → `USED` / `EXPIRED`
- PENDING: 세션 생성 직후 (15분 유효)
- COMPLETED: Lambda 콜백으로 S3 업로드 확인
- USED: /verifications에서 사용됨
- EXPIRED: 15분 초과 PENDING → 스케줄러가 만료 처리

### 스케줄러 3개

| 스케줄러 | 주기 | 역할 |
|---------|------|------|
| `CompleteExpiredCrewsScheduler` | 매일 03:00 KST | 만료 크루 COMPLETED 처리, 챌린지 ENDED |
| `FailExpiredChallengesScheduler` | 5분마다 | 마감 초과 챌린지 FAILED, 다음 사이클 자동 생성 |
| `ExpireUploadSessionScheduler` | 5분마다 | 15분 초과 PENDING 세션 EXPIRED 처리 |

---

## 7. 코드 컨벤션 및 규칙

### 필수 규칙 (CLAUDE.md 기반)

```java
// ✅ 의존성 주입: @RequiredArgsConstructor + private final
@RequiredArgsConstructor
public class SomeService {
    private final SomePort somePort;
}

// ✅ DTO: Java record 사용
public record CreateCrewRequest(String name, String goal) {}

// ✅ 예외: BusinessException(ErrorCode) 사용
throw new BusinessException(ErrorCode.CREW_FULL);

// ✅ Javadoc: 모든 public 메서드에 한 줄 한국어
/** 초대코드로 크루 조회 — 크루 참여 시 사용 */
Optional<Crew> findByInviteCode(String inviteCode);

// ✅ 모든 응답: ApiResponse<T> 래핑
return ApiResponse.success(result);
```

### 금지 사항

```java
// ❌ @Autowired 필드 주입
@Autowired private SomeService service;

// ❌ RuntimeException 직접 throw
throw new RuntimeException("에러");

// ❌ Controller에 비즈니스 로직
// ❌ Domain에서 JPA/HTTP 의존
// ❌ Port 없이 Adapter 직접 참조
// ❌ @Transactional 내부에서 S3 API 호출
// ❌ Entity를 Controller에서 직접 반환
```

### 패키지 구조 규칙

각 바운디드 컨텍스트 내부:
```
{context}/
├── api/           Controller, Request/Response DTO
├── application/   UseCase 구현체 (@Transactional 쓰기)
├── domain/
│   ├── model/     Entity, Aggregate Root (순수 POJO)
│   └── vo/        Value Object (enum 포함)
├── infra/         JPA, MyBatis, External API Adapter
└── port/
    ├── in/        UseCase 인터페이스
    └── out/       Repository Port, External Port 인터페이스
```

### 테스트 규칙

- BDD 스타일: Given-When-Then 주석으로 구조화
- 성공 케이스 + 예외 케이스(Unhappy Path) 최소 1개 이상
- Cucumber feature 파일로 인수 테스트 작성

### 커밋 메시지

```
<type>: <한국어 메시지>
- 부연 설명 (선택)

# type: feat | fix | refactor | test | docs | chore
```

---

## 8. 주의사항

### 보안

| 항목 | 상태 | 주의 |
|------|------|------|
| `POST /auth/test-login` | dev/test 전용 | `@Profile("!prod")` 로 prod에서 빈 로드 차단 |
| `X-User-Id` 헤더 | dev/test 전용 | DevSecurityConfig에서만 허용 |
| `/internal/**` 경로 | prod에서 denyAll | Phase 2: VPC 내부 + secret header 검증 예정 |
| JWT_SECRET | 환경변수 | `${JWT_SECRET}` — 하드코딩 절대 금지 |
| S3 CORS | 수동 설정 필요 | Pre-signed URL 사용 전 S3 버킷 CORS 설정 필수 |

### 아키텍처 함정

| 함정 | 올바른 접근 |
|------|------------|
| Pre-signed URL 생성을 S3 통신으로 오해 | 로컬 서명 생성 — @Transactional 안에서 해도 OK |
| 세션 COMPLETED 처리를 /verifications에서 | Lambda → /internal API에서 (트랜잭션 분리) |
| SSE 타임아웃 60초 무시 | 클라이언트는 fallback 폴링 대비 필요 |
| 닉네임을 카카오에서 동기화 | 닉네임은 서비스 고유값 — 재로그인 시 동기화 금지 |
| 인증 마감 기준 시간 | `upload_session.requested_at` 사용 (서버 기록, 불변) |

### 비즈니스 규칙 핵심

- 닉네임 유효성: `^[가-힣a-zA-Z0-9_]{2,12}$`
- 크루 정원: 2~10명
- 챌린지 마감: `crew.deadlineTime` (기본 23:59:59)
- 인증 중복 방지: UNIQUE(user_id, crew_id, target_date)
- 신고 임계값: `report_count >= 3` → 자동 검토 큐 (Phase 2)

---

## 9. 진행중 / 예정 작업

### Phase 1 잔여 작업 (MVP 완성 목표)

| 작업 | 상태 | 우선순위 |
|------|------|---------|
| GitHub Actions CI/CD 설정 | ❌ 미착수 | 높음 |
| AWS 프로덕션 배포 (EC2+RDS+S3+Lambda) | ❌ 미착수 | 높음 |
| `/internal/**` VPC 네트워크 게이팅 | ❌ 미착수 | 높음 |
| Moderation API/Service 구현 | ❌ 미착수 | 중간 |
| Support API/Service (반응 기능) | ❌ 미착수 | 중간 |
| 크루 탈퇴/강제퇴장 API | ❌ 미착수 | 중간 |

### Phase 2 예정 (스케일업)

- Redis 캐시 (크루 목록, 피드 페이지네이션)
- AWS SQS 비동기 이벤트 (알림, 챌린지 상태 전환)
- FCM 푸시 알림
- Redis 분산 락 (동시 크루 참여 정원 초과 방지)
- 토큰 블랙리스트 (logout Phase 2)
- JPA vs MyBatis 성능 벤치마크

### PR 리뷰 TODO (보류 중)

`/docs/archive/20260301-upload-session-review-todo.md` 참조:
- 업로드 세션 관련 4개 개선 사항 보류 중

---

## 10. 핵심 파일 레퍼런스

### 정본 문서 (먼저 읽을 것)

| 문서 | 경로 |
|------|------|
| API 명세 | `docs/spec/api-spec.md` |
| DB 스키마 | `docs/spec/schema.md` |
| 비즈니스 규칙 | `docs/spec/biz-logic.md` |
| 에이전트 컨벤션 | `CLAUDE.md` |

### 핵심 소스 파일

| 파일 | 역할 |
|------|------|
| `common/exception/ErrorCode.java` | 42개 에러코드 정의 |
| `common/exception/GlobalExceptionHandler.java` | 전역 예외 처리 |
| `common/auth/JwtProvider.java` | 토큰 생성/검증 |
| `common/auth/SecurityConfig.java` | 보안 설정 (prod) |
| `crew/domain/model/Crew.java` | 크루 Aggregate Root |
| `crew/domain/model/Challenge.java` | 챌린지 3일 사이클 로직 |
| `verification/domain/model/UploadSession.java` | 업로드 세션 상태 머신 |
| `verification/application/CreateVerificationService.java` | 인증 생성 + 마감 검증 |

---

*이 문서는 2026-03-04 기준 프로젝트 상태를 반영한다.*
*최신 변경사항은 `docs/log/debugging-log.md`와 git log를 참조.*
