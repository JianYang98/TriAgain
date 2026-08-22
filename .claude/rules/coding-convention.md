---
description: Java 코드 작성/수정/리팩토링 시 참고하는 코딩 컨벤션 (패키지 구조, 계층별 규약, 네이밍, DI, DTO, SQL, 예외처리, 주석)
paths: "src/main/**/*.java"
---

# Coding Convention

## 패키지 구조

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
└── infra/             // JPA, S3, SSE Adapter
```

## 계층별 규약

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

## 네이밍 규칙

- 메서드: camelCase (`createVerification`, `findByCrewId`)
- 클래스: PascalCase (`VerificationController`, `CrewJoinFacade`)
- 상수: UPPER_SNAKE_CASE (`MAX_CREW_MEMBERS`)
- 패키지: lowercase (`verification`, `crew`)

## 의존성 주입

- 모든 의존성은 `@RequiredArgsConstructor` + `private final`로 생성자 주입
- `@Autowired` 필드 주입 금지

## DTO

- Java `record` 사용 (Lombok 의존 없이 불변 객체)
- Entity를 Controller에서 직접 반환 금지, 반드시 DTO로 변환

## Native SQL 작성 컨벤션

- SELECT 컬럼은 줄바꿈 + 들여쓰기로 나열
- JOIN / LEFT JOIN은 줄바꿈, ON 조건은 3칸 들여쓰기
- 복합 ON 조건은 AND를 줄바꿈 + 2칸 들여쓰기로 정렬
- WHERE / AND는 줄바꿈 + 2칸 들여쓰기

```sql
SELECT DISTINCT
       cm.user_id,
       u.fcm_token,
       c.id   AS crew_id,
       c.name AS crew_name
FROM crews c
JOIN crew_members cm
   ON cm.crew_id = c.id
LEFT JOIN verifications v
   ON v.user_id = cm.user_id
  AND v.crew_id = c.id
  AND v.target_date = :targetDate
JOIN users u
   ON u.id = cm.user_id
WHERE c.status = 'ACTIVE'
  AND v.id IS NULL
```

## 예외 처리

- 커스텀 예외 사용 (`BusinessException` 상속)
- `throw new RuntimeException()` 금지
- 도메인별 구체적 예외 정의 (예: `CrewFullException`, `VerificationDeadlineException`)

## 주석

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
