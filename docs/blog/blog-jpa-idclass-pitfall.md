# JPA @IdClass 복합 PK의 함정 — DB 스키마와 엔티티 매핑 불일치 디버깅기

사이드 프로젝트 [TriAgain(작심삼일 크루)](https://github.com/triagain)을 만들고 있다.
"작심삼일도 괜찮아"를 모토로, 3일 단위 챌린지를 소규모 크루와 함께하는 습관 형성 서비스다.
Spring Boot 3.4 + JPA, 헥사고날 아키텍처 기반으로 백엔드를 구성하고 있었는데 — 크루 생성 API를 호출했더니 INSERT가 실패했다.
Claude Code와 함께 디버깅하면서 원인을 추적한 과정을 기록한다.

---

## 1. 에러 로그 — 무슨 일이 일어났나

크루 생성 API를 호출하면 내부에서 `crews` 테이블과 `crew_members` 테이블에 INSERT가 발생한다.
크루장(LEADER)을 멤버로 자동 등록하는 로직이다. 그런데 이런 에러가 터졌다.

```
ERROR: null value in column "id" of relation "crew_members" violates not-null constraint
Detail: Failing row contains (null, USR-abc123, CREW-xyz789, LEADER, 2026-02-28 15:30:00)
```

Hibernate가 생성한 SQL을 확인해보니:

```sql
insert into crew_members (joined_at, role, crew_id, user_id) values (?, ?, ?, ?)
```

**`id` 컬럼이 INSERT 문에 아예 없다.**

4개 컬럼만 INSERT하고 있고, DB가 기대하는 PK 컬럼인 `id`는 빠져 있었다.
DB 입장에서는 NOT NULL PK인 `id`에 값이 안 들어오니 당연히 제약 조건 위반이다.

---

## 2. 원인 분석 — DB 스키마 vs JPA 엔티티 매핑 불일치

### DB 스키마

`crew_members` 테이블은 서로게이트 PK(`id`)를 사용하도록 설계되어 있었다.

```sql
CREATE TABLE crew_members (
    id         VARCHAR(36) PRIMARY KEY,  -- 서로게이트 PK
    user_id    VARCHAR(36) NOT NULL,
    crew_id    VARCHAR(36) NOT NULL,
    role       VARCHAR(20) NOT NULL,
    joined_at  TIMESTAMP   NOT NULL
);
```

ERD에서도 명확하게 `id`가 PK다:

```
crew_members {
    string id PK
    string user_id FK
    string crew_id FK
    enum role
    timestamp joined_at
}
```

### JPA 엔티티 — 문제의 코드

그런데 JPA 엔티티는 `@IdClass`로 `(userId, crewId)` 복합 PK를 선언하고 있었다.

```java
@Entity
@Table(name = "crew_members")
@IdClass(CrewMemberId.class)   // ← 복합 PK 선언
public class CrewMemberJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;     // ← PK 1

    @Id
    @Column(name = "crew_id", nullable = false, length = 36)
    private String crewId;     // ← PK 2

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrewRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}
```

복합 PK용 ID 클래스도 있었다:

```java
public class CrewMemberId implements Serializable {
    private String userId;
    private String crewId;

    // equals, hashCode 구현 ...
}
```

### 불일치 그림

```
DB 스키마가 기대하는 것:
┌──────────────────────────────────────────────────────┐
│ crew_members                                         │
├──────────┬───────────┬───────────┬────────┬──────────┤
│ id (PK)  │ user_id   │ crew_id   │ role   │ joined_at│
│ NOT NULL │ NOT NULL  │ NOT NULL  │        │          │
└──────────┴───────────┴───────────┴────────┴──────────┘

JPA가 생성하는 INSERT:
INSERT INTO crew_members (joined_at, role, crew_id, user_id)
                                          ↑ PK      ↑ PK
→ id 컬럼이 INSERT 문에 아예 없다!
```

### 왜 이런 일이 발생하는가

`@IdClass`를 쓰면 **Hibernate는 `@Id`가 붙은 필드들만 PK로 인식**한다.
DB에 `id`라는 컬럼이 있든 말든, JPA 매핑에 해당 필드가 없으면 Hibernate는 INSERT SQL을 만들 때 그 컬럼을 **아예 무시**한다.

즉, Hibernate 입장에서는:
- PK = `(user_id, crew_id)` — `@Id` 어노테이션이 여기 붙어 있으니까
- `id` 컬럼? — 매핑된 필드가 없으니 INSERT에서 제외

DB 입장에서는:
- PK = `id` — DDL에 PRIMARY KEY로 정의되어 있으니까
- `id`에 값이 안 들어옴 → NOT NULL 위반

**JPA 엔티티가 생각하는 PK와 DB가 기대하는 PK가 서로 달랐던 것이다.**

---

## 3. Claude Code와 함께 원인 추적하기

위 분석을 혼자서 한 것은 아니다. Claude Code(AI 코딩 에이전트)와 페어 디버깅하면서 원인을 찾아갔다.
실제 대화 흐름을 재구성하면 이런 식이었다.

### 에러 로그 공유

> **나**: 크루 생성 API 호출하면 INSERT에서 에러가 나. 에러 로그랑 Hibernate SQL 같이 볼래?
> ```
> ERROR: null value in column "id" of relation "crew_members" violates not-null constraint
> ```
> ```sql
> insert into crew_members (joined_at, role, crew_id, user_id) values (?, ?, ?, ?)
> ```

> **Claude**: INSERT SQL을 보면 `id` 컬럼이 아예 빠져 있습니다.
> `crew_members` 테이블의 DDL과 `CrewMemberJpaEntity`의 `@Id` 매핑을 비교해 볼까요?
> 에러 메시지가 "null value in column id"이니까, Hibernate가 `id`를 INSERT 대상에서 제외하고 있다는 뜻입니다.

### 스키마 비교

> **나**: schema.md에 정의된 crew_members 테이블이야.
> ```sql
> CREATE TABLE crew_members (
>     id         VARCHAR(36) PRIMARY KEY,
>     user_id    VARCHAR(36) NOT NULL,
>     crew_id    VARCHAR(36) NOT NULL,
>     ...
> );
> ```

> **Claude**: 찾았습니다. DB 스키마에는 `id`가 서로게이트 PK인데,
> `CrewMemberJpaEntity`는 `@IdClass(CrewMemberId.class)`로 `(userId, crewId)` 복합 PK를 선언하고 있습니다.
> **DB의 PK 전략과 JPA의 PK 전략이 불일치**합니다.
>
> 프로젝트의 다른 엔티티들도 한번 확인해 볼게요.

### 전수 스캔 — AI의 강점이 드러나는 순간

여기서 Claude Code의 강점이 나왔다. 프로젝트 내 모든 JPA 엔티티 파일을 순식간에 탐색해서 `@Id` 패턴을 비교했다.

> **Claude**: 프로젝트 전체 엔티티를 스캔해 봤습니다.
>
> | 엔티티 | PK 전략 |
> |--------|---------|
> | `CrewJpaEntity` | 서로게이트 `@Id` (String id) |
> | `UserJpaEntity` | 서로게이트 `@Id` (String id) |
> | `ChallengeJpaEntity` | 서로게이트 `@Id` (String id) |
> | `VerificationJpaEntity` | 서로게이트 `@Id` (String id) |
> | `UploadSessionJpaEntity` | 서로게이트 `@Id` (String id) |
> | **`CrewMemberJpaEntity`** | **`@IdClass` 복합 PK** |
>
> **`CrewMember`만 유일하게 다른 패턴을 쓰고 있습니다.**
> 서로게이트 PK로 통일하면 DB 스키마와 일치하게 됩니다.
> 수정이 필요한 파일 4개를 정리해 드릴까요?

### 수정안 제시 → 리뷰 후 적용

> **Claude**: 수정 파일 4개입니다:
> 1. `CrewMember` 도메인 모델 — `id` 필드 + `IdGenerator.generate("CRMB")` 추가
> 2. `CrewMemberJpaEntity` — `@IdClass` 제거, 서로게이트 PK로 변경
> 3. `CrewMemberJpaRepository` — 타입 파라미터 `CrewMemberId` → `String`
> 4. `CrewMemberId` 클래스 — 삭제

> **나**: 좋아, 다른 엔티티 패턴이랑 동일하게 맞추자. 적용해 줘.

수정 적용 후 크루 생성 API를 다시 호출하니 정상 INSERT가 되었다.

### AI 페어 디버깅에서 느낀 점

이 디버깅에서 **AI의 강점**은 두 가지였다:

1. **에러 메시지에서 핵심을 즉시 짚는 것** — "id 컬럼이 INSERT에서 빠져 있다"를 바로 지적했다. 사람이라면 먼저 여러 가능성을 떠올리며 시간을 쓸 수 있는 부분이다.
2. **프로젝트 전체를 빠르게 스캔하는 것** — 모든 엔티티의 `@Id` 패턴을 비교해서 "하나만 다르다"를 찾아내는 작업은, 사람이 수동으로 하면 꽤 번거롭다.

반대로 **사람의 역할**도 분명했다:
- **컨텍스트 제공** — 에러 로그, 스키마 문서, 프로젝트 구조를 공유하는 건 사람의 몫이다
- **최종 판단** — AI가 제시한 수정안이 프로젝트 컨벤션에 맞는지, 사이드 이펙트가 없는지 판단하는 건 사람이 해야 한다

---

## 4. 왜 다른 엔티티는 문제없었나?

프로젝트의 모든 엔티티를 점검해 보니, **`CrewMember`만 유일하게 `@IdClass` 복합 PK를 사용하고 있었다.**
나머지는 전부 서로게이트 PK 패턴으로 통일되어 있었다.

예를 들어 `Crew` 도메인:

```java
// Crew 도메인 — 팩토리 메서드에서 PREFIX-UUID 형식 ID 생성
public static Crew create(String creatorId, ...) {
    String crewId = IdGenerator.generate("CREW");
    // → "CREW-a1b2c3d4e5f67890"
    return new Crew(crewId, ...);
}
```

```java
// CrewJpaEntity — 서로게이트 PK
@Entity
@Table(name = "crews")
public class CrewJpaEntity {
    @Id
    @Column(length = 36)
    private String id;  // 서로게이트 PK, 깔끔하다
    // ...
}
```

ID 생성 유틸리티는 `PREFIX-UUID` 형식의 식별 가능한 고유 ID를 만든다:

```java
public final class IdGenerator {
    /** PREFIX-UUID 형식 ID 생성 — 엔티티별 식별 가능한 고유 ID */
    public static String generate(String prefix) {
        return prefix + "-" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 16);
    }
}
// 예: "CREW-a1b2c3d4e5f67890", "USR-f9e8d7c6b5a43210"
```

### 엔티티별 ID 전략 비교

| 엔티티 | PK 전략 | ID 형식 | 문제 여부 |
|--------|---------|---------|----------|
| `Crew` | 서로게이트 `@Id` | `CREW-{uuid}` | 없음 |
| `User` | 서로게이트 `@Id` | 카카오 ID (String) | 없음 |
| `Challenge` | 서로게이트 `@Id` | `CHAL-{uuid}` | 없음 |
| `Verification` | 서로게이트 `@Id` | `VERF-{uuid}` | 없음 |
| `UploadSession` | 서로게이트 `@Id` | `UPSE-{uuid}` | 없음 |
| **`CrewMember`** | **`@IdClass` 복합 PK** | **(userId, crewId)** | **INSERT 실패** |

**프로젝트 전체에서 하나만 다른 패턴을 쓰고 있었고, 그게 정확히 버그의 원인이었다.**

"한 놈만 다르면 그 놈이 문제다"라는 디버깅 경험칙이 여기서도 통했다.

---

## 5. 해결 — 서로게이트 PK로 통일

수정 파일 4개. 핵심은 `@IdClass`를 제거하고 다른 엔티티와 동일한 서로게이트 PK 패턴으로 맞추는 것이다.

### (1) 도메인 모델에 `id` 필드 추가

```java
// Before — id 없이 userId + crewId만으로 식별
public class CrewMember {
    private final String userId;
    private final String crewId;
    private final CrewRole role;
    private final LocalDateTime joinedAt;

    public static CrewMember createLeader(String userId, String crewId) {
        return new CrewMember(userId, crewId, CrewRole.LEADER, LocalDateTime.now());
    }
}
```

```java
// After — 서로게이트 ID 추가
public class CrewMember {
    private final String id;        // ← 추가
    private final String userId;
    private final String crewId;
    private final CrewRole role;
    private final LocalDateTime joinedAt;

    public static CrewMember createLeader(String userId, String crewId) {
        return new CrewMember(
                IdGenerator.generate("CRMB"),  // ← 서로게이트 ID 생성
                userId, crewId, CrewRole.LEADER, LocalDateTime.now());
    }
}
```

도메인 모델이 스스로 ID를 생성한다. DB나 JPA에 의존하지 않는 순수 도메인 로직이다.

### (2) JPA 엔티티 — `@IdClass` 제거, 서로게이트 PK로 변경

```java
// Before — @IdClass 복합 PK
@Entity
@Table(name = "crew_members")
@IdClass(CrewMemberId.class)
public class CrewMemberJpaEntity {
    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;     // 복합 PK

    @Id
    @Column(name = "crew_id", nullable = false, length = 36)
    private String crewId;     // 복합 PK
```

```java
// After — 서로게이트 PK
@Entity
@Table(name = "crew_members")
public class CrewMemberJpaEntity {
    @Id
    @Column(length = 36)
    private String id;                                // 서로게이트 PK

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;                            // 일반 컬럼

    @Column(name = "crew_id", nullable = false, length = 36)
    private String crewId;                            // 일반 컬럼
```

`@Id`가 `id` 필드 하나에만 붙었다. Hibernate가 생성하는 INSERT에 `id` 컬럼이 포함되면서 DB 스키마와 일치한다.

### (3) Repository 타입 파라미터 변경

```java
// Before
public interface CrewMemberJpaRepository
    extends JpaRepository<CrewMemberJpaEntity, CrewMemberId> { }

// After
public interface CrewMemberJpaRepository
    extends JpaRepository<CrewMemberJpaEntity, String> { }
```

### (4) `CrewMemberId` 클래스 삭제

복합 PK용 ID 클래스는 더 이상 필요 없으므로 삭제했다.

```java
// 삭제됨
public class CrewMemberId implements Serializable {
    private String userId;
    private String crewId;
    // equals, hashCode ...
}
```

### 변경 요약

| 변경 대상 | Before | After |
|-----------|--------|-------|
| `CrewMember` (도메인) | `id` 없음 | `IdGenerator.generate("CRMB")` |
| `CrewMemberJpaEntity` | `@IdClass` + `@Id` 2개 | `@Id` 1개 (서로게이트) |
| `CrewMemberJpaRepository` | `JpaRepository<..., CrewMemberId>` | `JpaRepository<..., String>` |
| `CrewMemberId` | 존재 | **삭제** |

---

## 6. 교훈 — 체크리스트

이번 디버깅에서 얻은 교훈을 체크리스트로 정리한다.

### DB-JPA 매핑 점검

- [ ] **DB 스키마와 JPA 엔티티의 PK 전략이 일치하는가?**
  - DB에 `id` PK가 있으면 JPA에도 `@Id`가 `id` 필드에 있어야 한다
  - `@IdClass`나 `@EmbeddedId` 사용 시, DB 테이블에 별도 `id` PK 컬럼이 없는지 반드시 확인
- [ ] **프로젝트 전체의 ID 전략이 통일되어 있는가?**
  - 하나만 다른 패턴을 쓰고 있으면 버그 후보 1순위
- [ ] **Hibernate가 생성하는 SQL을 확인했는가?**
  - `spring.jpa.show-sql=true`로 INSERT문의 컬럼 목록을 확인할 것
  - 빠진 컬럼이 있다면 매핑 불일치 의심

### `@IdClass` / `@EmbeddedId` 사용 시 주의사항

- `@IdClass`를 쓰면 Hibernate는 `@Id`가 붙은 필드들만 PK로 인식한다
- DB에 서로게이트 PK(`id`)가 별도로 있다면, **복합 PK가 아니라 서로게이트 PK + UNIQUE 제약 조건**이 맞는 설계다
- 복합 PK가 정말 필요한 경우: DB 테이블의 PK도 복합 키여야 한다 (별도 `id` 없이 `(user_id, crew_id)` 자체가 PK)

### INSERT 실패 시 디버깅 순서

1. **에러 메시지의 컬럼명 확인** — "column X violates not-null" → X가 INSERT에 빠져 있다
2. **Hibernate가 생성한 SQL의 컬럼 목록 확인** — 빠진 컬럼이 있는가?
3. **JPA 엔티티의 `@Id` 매핑과 DB DDL의 PK 정의 비교** — 일치하는가?
4. **프로젝트 내 다른 엔티티와 패턴 비교** — 혼자만 다른 패턴을 쓰고 있지 않은가?

---

## 마무리

정리하면 **DB는 서로게이트 PK(`id`)를 기대하는데, JPA는 복합 PK(`userId + crewId`)를 쓰고 있어서 INSERT 시 `id` 컬럼이 빠진 것**이 원인이었다.

이 문제가 교묘한 이유는 — 컴파일 에러도 안 나고, 애플리케이션 시작도 정상이고, 실제로 INSERT를 실행하는 순간에야 터진다는 것이다.
`spring.jpa.hibernate.ddl-auto=validate`를 사용하면 시작 시점에 스키마 불일치를 잡을 수 있지만, 테스트 환경에서 `create-drop`을 쓰는 경우에는 JPA 매핑 기준으로 테이블이 새로 만들어지므로 불일치를 놓칠 수 있다.

DB 스키마를 먼저 설계하고 JPA 엔티티를 나중에 매핑하는 프로젝트라면, **스키마의 PK 전략과 엔티티의 `@Id` 매핑이 일치하는지** 반드시 확인하자.

이번 디버깅은 Claude Code와 함께 진행했는데, 에러 메시지에서 핵심을 바로 짚어주고 프로젝트 전체 엔티티를 스캔해서 패턴 불일치를 찾아내는 과정이 인상적이었다. 혼자 삽질했으면 원인을 찾는 데 더 오래 걸렸을 것이다.

AI와 페어 디버깅한 경험을 별도 에피소드로 더 자세히 정리했다 → [AI와 페어 디버깅하기](./blog-ai-pair-debugging.md)
