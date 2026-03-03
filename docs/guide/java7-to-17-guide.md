# Java 7 → 17 핵심 문법 학습 가이드

> Java 7에서 바로 17로 넘어오면서 모르는 문법이 많은 상황.
> 이 프로젝트(TriAgain) 실제 코드를 예시로 활용하여 **Lambda/Stream → Record → Optional** 순서로 핵심 3가지를 학습한다.
> 모든 코드 예시는 실제 프로젝트 소스와 대조 검증 완료.

---

## 1단계: Lambda & Stream API (Java 8+)

### Lambda란?

Java 7에서는 익명 클래스를 이렇게 썼습니다:

```java
// Java 7 스타일
Collections.sort(names, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.compareTo(b);
    }
});
```

Java 8부터 **람다 표현식**으로 한 줄로 줄일 수 있습니다:

```java
// Java 8+ Lambda
Collections.sort(names, (a, b) -> a.compareTo(b));
```

**핵심 문법:** `(매개변수) -> { 본문 }` — 본문이 한 줄이면 `{}`와 `return` 생략 가능

### Stream API란?

컬렉션을 **파이프라인 방식으로** 처리하는 API입니다.

```
컬렉션.stream()     // 스트림 생성
    .filter(...)    // 중간 연산 (걸러내기)
    .map(...)       // 중간 연산 (변환)
    .toList();      // 최종 연산 (결과 수집)
```

### 프로젝트 예시들

#### (1) anyMatch — "하나라도 조건에 맞으면 true"

**파일:** `crew/domain/model/Crew.java` (140-143행)

```java
// Java 7이었다면...
private boolean isAlreadyMember(String userId) {
    for (CrewMember m : this.members) {
        if (m.getUserId().equals(userId)) {
            return true;
        }
    }
    return false;
}

// Java 8+ Stream
private boolean isAlreadyMember(String userId) {
    return this.members.stream()
            .anyMatch(m -> m.getUserId().equals(userId));
}
```

#### (2) map + toList — "각 요소를 변환해서 새 리스트 만들기"

**파일:** `crew/application/GetMyCrewsService.java` (20-33행)

```java
// Java 7이었다면...
List<CrewSummaryResult> results = new ArrayList<>();
for (Crew crew : crewList) {
    results.add(new CrewSummaryResult(
        crew.getId(), crew.getName(), crew.getGoal(), ...
    ));
}
return results;

// Java 8+ Stream
return crewRepositoryPort.findAllByUserId(userId).stream()
        .map(crew -> new CrewSummaryResult(
                crew.getId(),
                crew.getName(),
                crew.getGoal(),
                crew.getVerificationType(),
                crew.getCurrentMembers(),
                crew.getMaxMembers(),
                crew.getStatus(),
                crew.getStartDate(),
                crew.getEndDate(),
                crew.getCreatedAt()
        ))
        .toList();
```

#### (3) 메서드 레퍼런스 `::` — 람다를 더 짧게

**파일:** `crew/infra/CrewJpaAdapter.java` (77행)

```java
// 람다 버전
.map(c -> c.toDomain())

// 메서드 레퍼런스 버전 (동일한 동작)
.map(CrewJpaEntity::toDomain)
```

`클래스명::메서드명` 은 `(인스턴스) -> 인스턴스.메서드()` 의 축약형입니다.

#### (4) forEach — "각 요소에 대해 작업 수행"

**파일:** `crew/application/ActivateCrewService.java` (40-48행)

```java
crew.getMembers().forEach(member -> {
    Challenge challenge = Challenge.createFirst(
            member.getUserId(),
            crew.getId(),
            startDate,
            deadline
    );
    challengeRepositoryPort.save(challenge);
});
```

#### (5) reduce — "모든 요소를 하나로 합치기"

**파일:** `common/exception/GlobalExceptionHandler.java` (26-29행)

```java
String message = e.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .reduce((a, b) -> a + ", " + b)
        .orElse("잘못된 입력값입니다.");
// 결과 예시: "name: 필수입니다, goal: 필수입니다"
```

### Stream 핵심 정리

| 메서드 | 용도 | 반환 |
|--------|------|------|
| `.filter(조건)` | 조건에 맞는 것만 남기기 | Stream |
| `.map(변환)` | 각 요소를 다른 형태로 변환 | Stream |
| `.anyMatch(조건)` | 하나라도 맞으면 true | boolean |
| `.toList()` | 리스트로 수집 (Java 16+) | List |
| `.forEach(작업)` | 각 요소에 작업 수행 | void |
| `.reduce(합치기)` | 모든 요소를 하나로 합침 | Optional |

---

## 2단계: Record (Java 16+)

### Record란?

**불변 데이터 클래스**를 한 줄로 만드는 문법입니다.

```java
// Java 7: 같은 걸 만들려면 이만큼 필요했음
public class JoinCrewRequest {
    private final String inviteCode;

    public JoinCrewRequest(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getInviteCode() { return inviteCode; }

    @Override public boolean equals(Object o) { ... }
    @Override public int hashCode() { ... }
    @Override public String toString() { ... }
}

// Java 16+ Record: 한 줄!
public record JoinCrewRequest(@NotBlank String inviteCode) {}
```

**자동 생성되는 것:** 생성자, getter(필드명과 동일 — `inviteCode()`), equals(), hashCode(), toString()

### 프로젝트 예시들

#### (1) 간단한 Request DTO

**파일:** `crew/api/JoinCrewRequest.java` (5행)

```java
public record JoinCrewRequest(@NotBlank String inviteCode) {}
```

#### (2) 여러 필드 + 검증 어노테이션

**파일:** `crew/api/CreateCrewRequest.java` (11-19행)

```java
public record CreateCrewRequest(
        @NotBlank String name,
        @NotBlank String goal,
        @NotNull VerificationType verificationType,
        @Min(1) @Max(10) int maxMembers,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean allowLateJoin
) {}
```

#### (3) Record에 메서드 추가 가능

**파일:** `common/response/ErrorResponse.java` (5-14행)

```java
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message);
    }
}
```

### Record 핵심 정리

| 특징 | 설명 |
|------|------|
| 불변 | 필드값 변경 불가 (final) |
| getter | `getName()` 이 아니라 `name()` |
| 검증 | `@NotBlank` 등 어노테이션 사용 가능 |
| 메서드 | static 메서드, 인스턴스 메서드 추가 가능 |
| 상속 | 다른 클래스 상속 불가 (interface 구현은 가능) |
| 용도 | DTO, Command, Value Object에 적합 |

---

## 3단계: Optional (Java 8+)

### Optional이란?

**null을 안전하게 다루는 래퍼 클래스**입니다. "값이 있을 수도 있고, 없을 수도 있다"를 타입으로 표현합니다.

```java
// Java 7: NullPointerException 위험!
User user = userRepository.findById(id);  // null일 수 있음
String name = user.getName();              // NPE!

// Java 8+: Optional로 안전하게
Optional<User> user = userRepository.findById(id);
String name = user.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                  .getName();
```

### 프로젝트 예시들

#### (1) orElseThrow — "없으면 예외 던지기" (가장 많이 쓰임)

**파일:** `user/application/GetUserService.java` (21-22행)

```java
User user = userRepositoryPort.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

#### (2) ifPresent — "있으면 실행, 없으면 무시"

**파일:** `moderation/infra/VerificationClientAdapter.java` (19-24행)

```java
verificationRepositoryPort.findById(verificationId)
        .ifPresent(verification -> {
            verification.hide();
            verificationRepositoryPort.save(verification);
        });
```

#### (3) map — "있으면 변환, 없으면 Optional.empty() 유지"

**파일:** `verification/infra/ChallengeClientAdapter.java` (18-21행)

```java
public Optional<ChallengeInfo> findChallengeById(String challengeId) {
    return challengeRepositoryPort.findById(challengeId)
            .map(this::toChallengeInfo);  // Challenge → ChallengeInfo 변환
}
```

#### (4) map + orElse 체이닝 — "변환 후 기본값"

**파일:** `moderation/infra/VerificationClientAdapter.java` (46-52행)

```java
return verificationRepositoryPort.findById(verificationId)
        .map(verification -> {
            verification.incrementReportCount();
            verificationRepositoryPort.save(verification);
            return verification.getReportCount();
        })
        .orElse(0);  // 없으면 0 반환
```

### Optional 핵심 정리

| 메서드 | 용도 | 이 프로젝트에서 |
|--------|------|----------------|
| `.orElseThrow(예외)` | 없으면 예외 | Service에서 도메인 조회 시 |
| `.ifPresent(작업)` | 있으면 실행 | Adapter에서 상태 변경 시 |
| `.map(변환)` | 값 변환 | Entity → Domain 변환 시 |
| `.orElse(기본값)` | 없으면 기본값 | 기본값이 필요할 때 |

### Optional 사용 규칙

```
✅ 반환 타입으로 사용:  Optional<User> findById(String id)
❌ 필드로 사용 금지:    private Optional<String> name  // 절대 X
❌ 매개변수로 사용 금지: void save(Optional<User> user)  // 절대 X
```
