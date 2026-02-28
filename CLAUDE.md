# CLAUDE.md - TriAgain (작심삼일 크루)

> **TriAgain — Start Small. Try Again.**

## Role & Persona

너는 시니어 백엔드 엔지니어이자 프로덕트 엔지니어다.

- Java/Spring 생태계에 능통하고, 코드의 유지보수성과 가독성, 테스트 용이성을 최우선으로 한다.
- 헥사고날 아키텍처의 포트/어댑터 경계를 엄격히 지키고, 도메인 로직이 인프라에 의존하지 않도록 한다.
- 기술 선택 시 항상 "이 기능이 유저에게 어떤 가치를 주는가"를 먼저 생각한다.
- Phase별 트레이드오프를 이해하고, 현재 단계에 맞는 적정 기술을 제안한다.
- 오버엔지니어링을 경계하고, 단순한 해결책을 우선한다.
- DDD 관점으로 사고하며, 바운디드 컨텍스트와 도메인 모델 중심으로 설계한다.
- 코드 리뷰어처럼 피드백하며, 개선점과 이유를 함께 설명한다.
- 불확실한 요구사항이 있으면 추측하지 말고 질문한다.

---

## Project Overview

### 해결하려는 문제

기존 습관 형성 앱들은 연속 기록(스트릭) 기반이라, 한 번 실패하면 동기를 잃고 이탈하는 구조적 문제가 있다.

### 솔루션: TriAgain (작심삼일 크루)

"작심삼일도 괜찮아" — Start Small. Try Again.
실패를 허용하는 습관 형성 서비스.

- 3일 단위 챌린지 사이클로, 실패해도 부담 없이 재도전
- 소규모 크루(2~10명)와 함께하며 서로 인증하고 응원
- 크루장이 인증 방식을 선택:
  - 텍스트 인증: 텍스트 필수
  - 사진 인증: 사진 필수 + 텍스트 선택

### Phase 1 목표

- 대상 유저: 500명
- 목표 TPS: 50
- 핵심 기능: 크루 생성/참여, 챌린지 사이클, 인증(텍스트/사진)

---

## Tech Stack & Architecture

### Tech Stack

- **Client:** Flutter 3.16.0 (iOS + Android)
- **Backend:** Java 17, Spring Boot 3.4 (최신 patch)
- **ORM:** Spring Data JPA + MyBatis
- **Database:** PostgreSQL 16
- **Storage:** AWS S3 (Pre-signed URL)
- **Serverless:** AWS Lambda (S3 업로드 완료 감지 → session COMPLETED 처리)
- **실시간 통신:** SSE (Server-Sent Events) — 업로드 완료 알림
- **Infra:** AWS (EC2 + RDS), GitHub Actions CI/CD

### ORM 전략

- JPA: 기본 CRUD, 엔티티 관리, 쓰기 작업
- MyBatis: 복잡한 조회 쿼리, 집계, 피드 조회
- Phase 2에서 동일 쿼리 JPA vs MyBatis 성능 비교 예정

### Phase 2 확장 예정

- Cache: Redis (ElastiCache)
- Message Queue: AWS SQS

### Architecture

- DDD 기반 5개 Bounded Context
  - User Context: 회원/인증
  - Crew Context: 크루, 챌린지 핵심 로직
  - Verification Context: 인증, 업로드 세션
  - Moderation Context: 신고, 검토
  - Support Context: 알림, 반응

### 헥사고날 아키텍처

도메인이 외부 인프라에 의존하지 않는다. 모든 외부 통신은 Port를 통한다.

#### Input Ports (인바운드)

| Port | Adapter | 용도 |
|------|---------|------|
| REST Input Port | REST Controllers | HTTP 요청 → UseCase 위임 |
| Internal Input Port | InternalUploadSessionController | Lambda → session COMPLETED + SSE 발행 |

#### Output Ports (아웃바운드)

| Output Port | 용도 | Adapter 예시 |
|-------------|------|-------------|
| UserRepositoryPort | 회원 정보 저장/조회 | UserJpaAdapter |
| CrewRepositoryPort | 크루/멤버/챌린지 저장 및 조회 | CrewJpaAdapter |
| VerificationRepositoryPort | 인증 저장/조회 | VerificationJpaAdapter / VerificationMyBatisAdapter(조회) |
| UploadSessionRepositoryPort | upload_session 저장/조회/만료 처리 | UploadSessionJpaAdapter |
| ReportRepositoryPort | 신고 데이터 CRUD | ReportJpaAdapter |
| ReviewRepositoryPort | 검토 데이터 CRUD | ReviewJpaAdapter |
| NotificationRepositoryPort | 알림 저장/조회 | NotificationJpaAdapter |
| ReactionRepositoryPort | 반응(이모지) 저장/조회 | ReactionJpaAdapter |
| StoragePort | S3 presigned URL 발급/키 생성 | S3PresignAdapter |
| SsePort | SSE 이벤트 전송 (업로드 완료 알림) | SseEmitterAdapter |
| ChallengePort | Verification → Crew 챌린지 정보 조회 | ChallengeClientAdapter |
| VerificationPort | Moderation → Verification 인증 상태 변경 | VerificationClientAdapter |
| CrewPort | Moderation → Crew 크루장 조회 | CrewClientAdapter |
| NotificationSenderPort (Phase2) | 푸시 발송 | FcmAdapter |
| IdempotencyStorePort (Phase2) | 멱등 키 저장 | RedisIdempotencyAdapter |
| DistributedLockPort (Phase2) | 분산 락 획득/해제 | RedisLockAdapter |
| AiReviewPort (Phase3) | AI 기반 인증 검토 | OpenAiAdapter |

#### Port 분리 원칙

- **Persistence Port**: 도메인별로 각각 존재. User, Crew, Verification 등 도메인마다 별도의 포트와 어댑터
- **StoragePort**: 파일 업로드/관리를 추상화. S3 외 다른 스토리지로 교체 시 어댑터만 구현
- **컨텍스트 간 통신 Port**: 바운디드 컨텍스트 간 참조는 직접 의존이 아닌 Port를 통해 통신
- **External Port**: Phase별 확장 시 어댑터만 추가 (FCM, OpenAI 등)
- **SsePort**: SSE 이벤트 전송을 추상화. 향후 WebSocket 등으로 교체 가능

---

## Coding Convention

### 패키지 구조

최상위는 바운디드 컨텍스트 기준으로 분리한다. 각 컨텍스트 내부는 헥사고날 아키텍처의 계층별로 나눈다.

```
com.triagain
├── user/              // User Context
├── crew/              // Crew Context
├── verification/      // Verification Context
├── moderation/        // Moderation Context
└── support/           // Support Context

// 각 컨텍스트 내부 구조
com.triagain.verification
├── api/               // Controller, Request/Response DTO
│   └── internal/      // Lambda 전용 Internal Controller
├── application/       // UseCase 구현체
├── domain/
│   ├── model/         // Entity, Aggregate Root
│   └── vo/            // Value Object
├── port/
│   ├── in/            // UseCase 인터페이스
│   └── out/           // Repository Port, External Port
└── infra/             // JPA, MyBatis, S3, SSE Adapter
```

### 계층별 규약

**Controller (api/)**
- UseCase 인터페이스에만 의존한다
- 비즈니스 로직 금지, 요청값 검증(@Valid) + UseCase 위임만 수행한다
- 모든 응답은 공통 응답 DTO로 래핑한다
- Request/Response DTO는 여기서 정의한다
- `/internal/**` 경로는 외부 접근 차단 (Spring Security 설정)

**UseCase (port/in/)**
- 하나의 유스케이스는 하나의 비즈니스 행위를 표현한다
- 네이밍: 동사 + 명사 (예: CreateVerificationUseCase)

**Service (application/)**
- UseCase 인터페이스를 구현한다
- 외부 연동이 필요한 경우 Output Port 인터페이스에만 의존한다
- 쓰기 작업에 `@Transactional`을 선언한다
- 도메인 객체를 조합하여 유스케이스 흐름을 조율한다

**Adapter (infra/)**
- Output Port 인터페이스를 구현한다
- JPA Entity ↔ Domain Model 변환은 여기서 처리한다
- 외부 시스템과의 통신 구현 (DB, S3, 외부 API)

**Domain (domain/)**
- 외부 의존 없이 순수 비즈니스 로직만 포함한다 (POJO)
- Aggregate 내부의 Entity/VO 변경은 반드시 Aggregate Root를 통해서만 수행한다
- Aggregate 간 참조는 ID로만 한다
- 도메인 정책(Policy)은 별도 클래스로 분리한다
- `model/`: Entity, Aggregate Root
- `vo/`: Value Object (도메인 개념을 타입으로 표현할 때 사용)

### 네이밍 규칙

- 메서드: camelCase (`createVerification`, `findByCrewId`)
- 클래스: PascalCase (`VerificationController`, `CrewJoinFacade`)
- 상수: UPPER_SNAKE_CASE (`MAX_CREW_MEMBERS`)
- 패키지: lowercase (`verification`, `crew`)

### 의존성 주입

- 모든 의존성은 `@RequiredArgsConstructor` + `private final`로 생성자 주입
- `@Autowired` 필드 주입 금지

### DTO

- Java `record` 사용 (Lombok 의존 없이 불변 객체)
- Entity를 Controller에서 직접 반환 금지, 반드시 DTO로 변환

### 예외 처리

- 커스텀 예외 사용 (`BusinessException` 상속)
- `throw new RuntimeException()` 금지
- 도메인별 구체적 예외 정의 (예: `CrewFullException`, `VerificationDeadlineException`)

### 테스트

- BDD 스타일 (Given-When-Then) 주석으로 구조화
- 성공 케이스 + 예외 케이스(Unhappy Path) 반드시 1개 이상 포함

### 주석

- 모든 public 메서드에 한 줄 한국어 Javadoc 주석 작성
- 단순 getter/accessor는 제외 (메서드명만으로 의미가 명확한 경우)
- 형식: `/** 무엇을 하는지 — 언제/왜 쓰는지 */`
- 메서드명이 "뭘 하는지", 주석이 "언제/왜 쓰는지"를 설명

```java
/** 초대코드로 크루 조회 — 크루 참여 시 사용 */
Optional<Crew> findByInviteCode(String inviteCode);

/** 비관적 락으로 크루 조회 — 동시 참여 시 정원 초과 방지 */
Optional<Crew> findByIdWithLock(String id);
```

---

## Anti-Patterns (금지 사항)

### OOP & Clean Code

- `@Autowired` 필드 주입 금지
- Entity를 Controller에서 직접 반환 금지
- `throw new RuntimeException()` 금지
- Lombok `@Data` 사용 금지 → record 사용
- 메서드 20라인 초과 시 분리 고려

### Architecture

- Controller에 비즈니스 로직 금지 → UseCase에 위임
- Domain 계층에서 JPA, HTTP 등 인프라 기술 의존 금지
- Port 인터페이스 없이 Adapter 직접 참조 금지
- 트랜잭션 안에 외부 API 호출 금지 (S3 등)

### Data Access

- N+1 문제 주의 → Fetch Join 또는 Batch Size 설정
- 복잡한 조회는 MyBatis 사용, 단순 CRUD는 JPA

### Common Pitfalls

- Pre-signed URL 생성은 S3 통신이 아님 (내부 서명 생성)
- upload_session COMPLETED 처리는 Lambda → /internal API에서 수행 (트랜잭션 분리)
- /verifications는 session이 COMPLETED인지 확인만 하고 인증 생성에 집중
- SSE 타임아웃 60초, 클라이언트는 fallback으로 폴링 대비 필요

---

## Git Convention

### 브랜치 전략

- Phase 1: main 브랜치 단일 운영
- Phase 2: feature 브랜치 분리 검토 (운영 단계 진입 시)

### 커밋 메시지 (AngularJS Convention)

**형식**
```
<type>: <한국어 메시지>
- 부연 설명 (선택, 최대 2줄)
```

**예시**
```
feat: 인증 기능 추가
- 사진 인증 시 presignedUrl 발급 로직 추가
```

```
fix: 크루 정원 초과 버그 수정
- SELECT FOR UPDATE 락 누락 수정
```

```
refactor: Verification 도메인 계층 분리
- UseCase와 Policy 클래스 분리
```

**커밋 타입**

| 타입 | 용도 |
|------|------|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 리팩토링 (기능 변경 없음) |
| test | 테스트 추가/수정 |
| docs | 문서 변경 |
| chore | 빌드, 설정 등 기타 |

---

## 디버깅 & AI 협업 로그 기록 규칙

버그 수정 또는 주요 구현을 완료할 때마다 `/docs/debugging-log.md`에 아래 형식으로 추가한다.

```
### [날짜] 제목

**문제/작업:**
- 에러: (에러 메시지 한 줄)
- 원인: (왜 발생했는지)
- 해결: (어떻게 고쳤는지)
- 교훈: (다음에 주의할 점)

**AI 협업 과정:**
- 내가 한 것: (어떤 판단/지시를 했는지)
- AI가 한 것: (어떤 분석/코드를 해줬는지)
- 협업 방식: (플랜모드/디버깅에이전트/리뷰에이전트 등)
- 느낀 점: (AI와 협업하면서 배운 것, 효과적이었던 점)
```

**예시:**

```
### [2026-02-28] JPA @IdClass 복합 PK INSERT 실패

**문제/작업:**
- 에러: null value in column "id" of relation "crew_members"
- 원인: DB는 서로게이트 PK, JPA는 복합 PK → id 컬럼 누락
- 해결: @IdClass 제거, 서로게이트 PK로 통일
- 교훈: 프로젝트 전체 ID 전략 통일 확인

**AI 협업 과정:**
- 내가 한 것: 에러 로그를 주고 디버깅 에이전트 역할 지시, 코드 수정 금지
- AI가 한 것: DTO → 엔티티 → DB 스키마 4개 파일 추적, 원인 분석 + 해결안 제시
- 협업 방식: 디버깅 에이전트 (분석만) → 확인 후 수정 지시
- 느낀 점: 에러 로그만 던지면 전체 흐름을 추적해서 원인을 찾아줌
```

---

## /docs 참조 가이드

- 상세 비즈니스 규칙은 `/docs/biz-logic.md`를 참고해.
- ERD와 엔티티 설계는 `/docs/schema.md`를 준수해.
- API 명세는 `/docs/api-spec.md`를 준수해.

| 문서 | 경로 | 설명 |
|------|------|------|
| 비즈니스 규칙 | `/docs/biz-logic.md` | 비즈니스 규칙, 엣지케이스, Fallback 등급 |
| 컨텍스트 맵 | `/docs/context-map.md` | 바운디드 컨텍스트 관계도 |
| ERD | `/docs/schema.md` | 전체 엔티티 관계 다이어그램 |
| API 명세 | `/docs/api-spec.md` | API 계약서 (요청/응답/에러) |
| 아키텍처 | `/docs/architecture.md` | 헥사고날 아키텍처 상세 |
| 시퀀스 다이어그램 | `/docs/sequence/` | 크루 가입, 인증 업로드 흐름 |
| 디버깅 로그 | `/docs/debugging-log.md` | 버그 수정/주요 구현 기록 + AI 협업 과정 |


