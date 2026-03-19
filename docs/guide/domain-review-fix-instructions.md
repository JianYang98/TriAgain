# [수정 지시] 도메인 리뷰 수정 — 백엔드 에이전트

## 배경
오케스트레이션 검증(`domain-review-comment.md`)에서 CRITICAL 1건, MAJOR 2건, Warning 2건 발견.
크루 가입 도메인 불변식 누출, 서비스 검증 중복, 초대코드 정규식 오탐을 수정한다.

---

## 수정 순서

### 1. Crew.java — INVITE_CODE_CHARS 상수 추출 + addMember 검증 추가 + create null 검증

**파일**: `src/main/java/com/triagain/crew/domain/model/Crew.java`

#### 1-1. INVITE_CODE_CHARS 상수 추출 (라인 245)

**현재** (라인 244-252):
```java
private static String generateInviteCode() {
    String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    // ...
}
```

**변경**:
```java
public static final String INVITE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

private static String generateInviteCode() {
    StringBuilder code = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
        int index = (int) (Math.random() * INVITE_CODE_CHARS.length());
        code.append(INVITE_CODE_CHARS.charAt(index));
    }
    return code.toString();
}
```

- `INVITE_CODE_CHARS`를 `public static final`로 클래스 상단 상수 영역에 선언 (라인 21 부근, `DEFAULT_DEADLINE_TIME` 아래)
- `generateInviteCode()` 내부의 `String chars` 로컬 변수를 제거하고 상수를 참조

#### 1-2. addMember()에 isJoinDeadlinePassed() 검증 추가 [CRITICAL]

**현재** (라인 122-137):
```java
public CrewMember addMember(String userId) {
    if (isFull()) {
        throw new BusinessException(ErrorCode.CREW_FULL);
    }
    if (canNotJoin()) {
        throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
    }
    if (isAlreadyMember(userId)) {
        throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
    }
    // ...
}
```

**변경** — `canNotJoin()` 검증과 `isAlreadyMember()` 검증 사이에 추가:
```java
public CrewMember addMember(String userId) {
    if (isFull()) {
        throw new BusinessException(ErrorCode.CREW_FULL);
    }
    if (canNotJoin()) {
        throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
    }
    if (isJoinDeadlinePassed()) {
        throw new BusinessException(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
    }
    if (isAlreadyMember(userId)) {
        throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
    }

    CrewMember member = CrewMember.createMember(userId, this.id);
    this.members.add(member);
    this.currentMembers++;
    return member;
}
```

**이유**: 도메인 불변식이 서비스에 누출되어 있었음. `addMember()`가 모든 가입 조건을 스스로 검증해야 어떤 서비스에서 호출하든 불변식이 보장됨.

#### 1-3. create()에 category null 검증 추가 (Warning)

**현재** (라인 71-104): `category`를 null 체크 없이 전달.

**변경** — `create()` 시작부에 추가:
```java
public static Crew create(..., CrewCategory category, CrewVisibility visibility) {
    Objects.requireNonNull(category, "category must not be null");
    validateMaxMembers(maxMembers);
    validateDates(startDate, endDate);
    // ... 기존 로직
}
```

**import 추가** 필요: `import java.util.Objects;`

**주의**: `of()` 팩토리에는 추가하지 않는다 (기존 데이터 호환성).

---

### 2. [삭제됨] SearchCrewsUseCase.java — isInviteCodeSearch() 정규식 수정

> 초대코드 검색 기능 자체가 제거됨 (GET /crews/search는 PUBLIC 크루 LIKE 검색만 수행).
> `INVITE_CODE_PATTERN`, `isInviteCodeSearch()` 메서드 모두 삭제.

---

### 3. JoinCrewService.java — validateJoin() 제거 [MAJOR]

**파일**: `src/main/java/com/triagain/crew/application/JoinCrewService.java`

**현재** (라인 31, 46-50):
```java
// 라인 31
validateJoin(crew, command.userId());

// 라인 46-50
private void validateJoin(Crew crew, String userId) {
    if (crew.isJoinDeadlinePassed()) {
        throw new BusinessException(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
    }
}
```

**변경**:
1. 라인 31의 `validateJoin(crew, command.userId());` 호출 삭제
2. 라인 46-50의 `validateJoin()` 메서드 전체 삭제
3. 사용하지 않게 된 import 정리: `ErrorCode` import가 다른 곳에서 쓰이면 유지, 안 쓰이면 제거

**결과물**:
```java
@Override
@Transactional
public JoinCrewResult joinCrew(JoinCrewCommand command) {
    Crew crew = crewRepositoryPort.findByIdWithLock(command.crewId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

    if (!crew.isPublic()) {
        throw new BusinessException(ErrorCode.CREW_NOT_PUBLIC);
    }

    CrewMember member = crew.addMember(command.userId());
    crewRepositoryPort.save(crew);
    crewRepositoryPort.saveMember(member);

    return new JoinCrewResult(
            member.getUserId(),
            member.getCrewId(),
            member.getRole(),
            crew.getCurrentMembers(),
            member.getJoinedAt()
    );
}
```

**이유**: Issue 1에서 `addMember()`에 deadline 검증을 추가했으므로, 서비스의 `validateJoin()`은 완전 중복. 도메인이 스스로 불변식을 보장하므로 서비스에서 이중 검증할 필요 없음.

---

### 4. JoinCrewByInviteCodeService.java — validateJoin() 제거 [MAJOR]

**파일**: `src/main/java/com/triagain/crew/application/JoinCrewByInviteCodeService.java`

**현재** (라인 29, 44-57):
```java
// 라인 29
validateJoin(lockedCrew, command.userId());

// 라인 44-57
private void validateJoin(Crew crew, String userId) {
    if (crew.canNotJoin()) {
        if (crew.isFull()) {
            throw new BusinessException(ErrorCode.CREW_FULL);
        }
        throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
    }
    if (crew.isJoinDeadlinePassed()) {
        throw new BusinessException(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
    }
    if (crew.getMembers().stream().anyMatch(m -> m.getUserId().equals(userId))) {
        throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
    }
}
```

**변경**:
1. 라인 29의 `validateJoin(lockedCrew, command.userId());` 호출 삭제
2. 라인 44-57의 `validateJoin()` 메서드 전체 삭제
3. 사용하지 않게 된 import 정리

**결과물**:
```java
@Override
@Transactional
public JoinByInviteCodeResult joinByInviteCode(JoinByInviteCodeCommand command) {
    Crew crew = crewRepositoryPort.findByInviteCode(command.inviteCode())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

    Crew lockedCrew = crewRepositoryPort.findByIdWithLock(crew.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

    CrewMember member = lockedCrew.addMember(command.userId());
    crewRepositoryPort.save(lockedCrew);
    crewRepositoryPort.saveMember(member);

    return new JoinByInviteCodeResult(
            member.getUserId(),
            member.getCrewId(),
            member.getRole(),
            lockedCrew.getCurrentMembers(),
            member.getJoinedAt()
    );
}
```

**이유**: `addMember()`가 isFull, canNotJoin, isJoinDeadlinePassed, isAlreadyMember를 모두 검증하므로 서비스의 `validateJoin()`과 완전 중복.

---

### 5~7. [삭제됨] findByInviteCodeForSearch 리네이밍

> 초대코드 검색 기능 자체가 제거됨.
> `findByInviteCodeForSearch()` 메서드가 CrewRepositoryPort, CrewJpaAdapter, SearchCrewsService에서 모두 삭제.

---

## 테스트 검증

### 기존 테스트 영향 분석

`CrewTest.java`의 `AddMember` 테스트들은 헬퍼 `crewWithStatus()`가 `endDate = FAR_FUTURE (now+30일)`을 사용하므로 `isJoinDeadlinePassed()` = false → **기존 테스트 깨지지 않음**.

단, `create()에 Objects.requireNonNull(category)` 추가 시 기존 `create()` 호출 중 `category = null`인 테스트가 **깨짐**.

**영향받는 테스트** (CrewTest.java):
- `Create.success()` — 라인 36: `Crew.create(..., null, null)` → category가 null → NPE
- `Create.minMembers()` — 라인 53
- `Create.maxMembers()` — 라인 62
- `Create.startDateToday()` — 라인 91
- `Create.startDatePast()` — 라인 101
- `Create.endDateEqualsStartDate()` — 라인 111
- `Create.endDateBeforeStartDate()` — 라인 121
- `Create.endDateTooClose()` — 라인 136
- `Create.endDateExactlyMinimumDuration()` — 라인 151

**수정 방법**: 위 테스트들의 `Crew.create()` 호출에서 `null` → `CrewCategory.EXERCISE` (또는 아무 유효값)으로 변경. 날짜 검증 테스트는 `Objects.requireNonNull`보다 뒤에서 터지므로 category만 유효값으로 넣으면 됨.

### 추가 필요 테스트

`AddMember` 섹션에 deadline 검증 테스트가 없으므로 추가:

```java
@Test
@DisplayName("참여 마감이 지난 크루에 addMember하면 CREW_JOIN_DEADLINE_PASSED 예외가 발생한다")
void joinDeadlinePassed() {
    // Given — endDate가 2일 뒤 → isJoinDeadlinePassed() = true
    Crew crew = crewWithEndDate(LocalDate.now().plusDays(2));

    // When & Then
    assertThatThrownBy(() -> crew.addMember("user2"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
}
```

`crewWithEndDate()` 헬퍼가 이미 존재하므로 그대로 사용 가능.

### 최종 검증

```bash
./gradlew test
```

모든 테스트 통과 확인 후 완료.

---

## 요약

| # | 파일 | 변경 유형 | 심각도 |
|---|------|----------|--------|
| 1-1 | Crew.java | INVITE_CODE_CHARS 상수 추출 | MAJOR 지원 |
| 1-2 | Crew.java | addMember()에 deadline 검증 추가 | CRITICAL |
| 1-3 | Crew.java | create()에 category null 검증 | Warning |
| 2 | SearchCrewsUseCase.java | 정규식을 상수 기반으로 변경 | MAJOR |
| 3 | JoinCrewService.java | validateJoin() 제거 | MAJOR |
| 4 | JoinCrewByInviteCodeService.java | validateJoin() 제거 | MAJOR |
| 5 | CrewRepositoryPort.java | 메서드 리네이밍 | Warning |
| 6 | CrewJpaAdapter.java | 메서드 리네이밍 | Warning |
| 7 | SearchCrewsService.java | 호출부 리네이밍 | Warning |
| - | CrewTest.java | create() 테스트 category 수정 + addMember deadline 테스트 추가 | 테스트 |
