---
description: 과거 실수에서 배운 교훈 — 구현 전 반드시 읽고 같은 실수 반복 방지
paths: "src/**/*.java"
---

# Lessons Learned — 실전에서 배운 교훈

> 에이전트가 실수할 때마다 여기에 교훈을 추가한다.
> "왜?" 근본 원인을 분석하고, 같은 실수를 두 번 하지 않기 위한 규칙을 남긴다.
> 에이전트는 매 세션 시작 시 이 파일을 읽고 숙지한다.

---

## 아키텍처

### Controller에 비즈니스 로직 넣지 마라
- 출처: user-profile SDD (2026-04-14)
- 사례: UserController가 StoragePort 직접 사용 + 파일 검증 + presigned URL 생성
- 왜?: 새 기능 빨리 구현하려고 기존 패턴(UseCase 분리) 안 따르고 Controller에 직접 로직 넣음. 헥사고날 구조를 무시한 것.
- 규칙: Controller는 순수 위임만. 검증/생성 로직은 반드시 UseCase/Service로 분리

### Cross-context 직접 import 금지
- 출처: user-profile SDD (2026-04-14)
- 사례: User context가 Verification의 StoragePort를 직접 import
- 왜?: StoragePort가 Verification context에만 있었는데, User에서도 S3 업로드가 필요해지자 빠른 해결을 위해 직접 가져다 씀. context 경계를 무시한 것.
- 규칙: 다른 context의 Port/Service 직접 사용 금지. 필요하면 자기 context에 Port 만들어서 통신

---

## 코드 품질

### 중복 vs 추출 — 기본은 추출, 유지는 근거를 남겨야 성립
- 출처: user-profile SDD (2026-04-14) + 승격 통합 (2026-07-25)
- 기본값: 같은 로직·유틸이 2곳 이상이면 추출한다. 신규 구현 전 기존 코드에 같은 역할의 메서드가 있는지 먼저 검색·재활용 (구 "중복 로직 만들지 마라" + "유틸 함수는 공유 파일로 빼라" 통합·승격)
- 판정 3문(①②③) — 하나라도 YES면 추출, 전부 NO면 유지 가능(마커 필수):
  - ① 한쪽만 고치고 다른 쪽 안 고치면 버그인가?
  - ② 둘이 항상 같이 바뀌는가?
  - ③ 이게 규칙/계약인가(검증·상태분류·API·비즈로직·보안)?
- 추출하기로 했으면 ④(경계 문항 — 판정 아님, "어디까지 뺄까"):
  - ④ 추출하면 flag/param을 2개 이상 끼워야 하는가? → YES면 통째로 추출하지 말고, 안쪽 지식(상수·검증 규칙·상태 분류)만 단일 소스로 빼고 껍데기(호출 골격)는 양쪽에 중복으로 둔다. 경계가 틀린 것이지 추출이 틀린 게 아니다
- 유지(3문 전부 NO) 결정 시 반드시 그 코드에 마커를 남긴다: `// 의도적 중복: ①NO(…) ③NO(…) — 판정 3문 전부 NO`. **BE는 전용 코드품질 리뷰어가 없어 이 마커가 유일한 안전장치다** — 마커 없으면 다음 구현/리뷰에서 같은 중복을 또 판단·논쟁한다
- 마커 무효화: 한 PR에서 마커 붙은 양쪽이 함께 수정되면 마커 무효(②=YES 실증) → 재평가. 이 마커 코드에서 동기화 누락 버그가 나면 ①번 오판으로 이 파일에 기록
- 재발: 1회
  - solo-habit /simplify (2026-07-10): 습관 소유/상태 가드(ENDED 제외+소유자+활성 검증)가 8개 서비스에 private 메서드로 near-verbatim 복붙됨. 크루 BC가 같은 패턴을 각 서비스에 인라인해 둔 "관행"을 그대로 답습한 것이 원인 — 신규 코드는 기존 부채를 복제하지 말고 이 규칙을 우선한다. 정적 헬퍼 `HabitAccessGuard.requireOwned/requireOwnedActive`로 추출(포트 호출은 호출자에 남겨 락 여부·테스트 스텁 무변경).

### 시간 윈도우 상수는 "발송 가능 구간"이 아닌 반대 의미 이름 붙이지 마라
- 출처: crew-first-verification /simplify (2026-06-17)
- 사례: `QUIET_START = 08:00`, `QUIET_END = 22:00` — 실제로는 발송 윈도우 시작/끝인데 "quiet"이라는 이름을 붙임. quiet period는 `[22:00, 08:00)`이므로 상수명과 값이 반대
- 왜?: 사용자 관점("이 시간 이외에는 quiet")과 코드 관점("이 상수로 skip 조건 판단")이 뒤섞임
- 규칙: 시간 윈도우 상수는 실제 그 값이 "무엇의 시작/끝"인지 이름으로 명확히 표현. 발송 가능 구간이면 `NOTIFY_START/NOTIFY_END`, quiet period 경계면 `QUIET_START/QUIET_END`

---

## DB / 쿼리

### 네이티브 쿼리 테이블명은 반드시 schema.md와 대조
- 출처: 회원탈퇴 버그 (2026-04-14)
- 사례: upload_sessions(복수형) 오타 → 실제 테이블은 upload_session(단수형)
- 왜?: 네이티브 쿼리를 작성할 때 테이블명을 기억에 의존함. JPA Entity의 @Table 어노테이션이나 schema.md를 확인하지 않은 것.
- 규칙: 네이티브 쿼리 작성 시 schema.md 또는 @Table 어노테이션과 반드시 대조 확인
- 재발: 1회
  - 인증 취소 SDD step1 조사 중 발견 (2026-07-23, 원 버그는 2026-03-03 커밋 `7d9fe5b`): **제약 이름을 컬럼명에서 추론**해 `GlobalExceptionHandler`의 분기가 약 4개월간 오매핑됐다. 상세는 아래 "DB 제약 이름으로 분기할 땐 마이그레이션에서 이름을 복사하라" 항목. **같은 `upload_session` 식별자에서 같은 유형(DB 식별자를 확인 없이 추론)의 실수가 반복된 것.**

### DB 제약 이름으로 분기할 땐 마이그레이션에서 이름을 복사하라 (contains 부분매칭 금지)
- 출처: 인증 취소·수정 SDD step1 조사 (2026-07-23). 원 버그는 `7d9fe5b`(2026-03-03) — `[2026-03-03] DataIntegrityViolation 기본 에러코드 V003 하드코딩 버그` 수정 커밋 자체가 새 버그를 심었다
- 사례: `GlobalExceptionHandler`가 `constraintName.contains("upload_session_id")` → `UPLOAD_SESSION_ALREADY_USED(V015)`로 분기했으나, **실제 제약 이름은 `uk_verifications_upload_session`으로 `_id`가 없다.** 이 분기는 한 번도 실행되지 않았고, 모든 위반이 다음 분기 `contains("verification")`로 흘러 **V003으로 오매핑**됐다. DB의 전체 제약 5건 중 4건이 틀린 코드를 반환:
  - `uk_verifications_upload_session`(세션 중복 사용) → V003 (정답: V015)
  - `uk_reports_verification_reporter`(**중복 신고**) → V003 "이미 해당 날짜에 인증이 존재합니다" (정답: M002)
  - `uk_habit_verifications_habit_date` / `..._upload_session`(습관) → V003
  - `UPLOAD_SESSION_ALREADY_USED(V015)`·`REPORT_ALREADY_EXISTS(M002)`는 **정의돼 있으나 도달 불가능한 死 코드**가 됐다
- 왜?: 세 가지가 겹쳤다.
  1. **식별자를 추론했다** — 커밋 메시지 `constraint name 기반 에러코드 분기 (verification, upload_session_id)`가 증거다. 하나는 테이블명(`verifications`)에서, 하나는 **컬럼명**(`upload_session_id`)에서 따왔다. 실제 제약 이름을 확인한 흔적이 없다. 마이그레이션 파일 1줄만 열어봤으면 즉시 보였을 값이다
  2. **`contains()` 부분매칭이 남의 도메인 제약을 조용히 삼켰다** — `contains("verification")`이 moderation의 `uk_reports_verification_reporter`까지 매칭. V1부터 있던 제약이라 처음부터 오매핑이었다
  3. **신규 제약 추가 시 기존 분기를 점검하지 않았다** — V23(solo-habit, 7월)이 `uk_habit_verifications_*` 2건을 추가했으나 분기는 갱신되지 않았다
- 왜 오래 안 들켰나: V003도 409라 HTTP 레벨에선 그럴듯했고, 세션 중복·중복 신고는 정상 사용에서 거의 발생하지 않으며(더블탭 정도), **제약 이름 → 에러코드 매핑을 검증하는 테스트가 없었다**. 로그에 `constraintName`이 찍히고는 있었다
- 규칙:
  1. 제약 이름으로 분기할 땐 **마이그레이션 파일에서 이름을 복사**한다. 컬럼명·테이블명에서 추론 금지 (SDD `sdd-doc-grounding.md`의 "실제 코드에서 복사" 원칙과 동일)
  2. **`contains()` 부분매칭 대신 정확 매칭 또는 명시적 상수 목록**을 쓴다. 부분매칭은 새 제약이 추가될 때 조용히 오매핑된다
  3. **새 UNIQUE 제약을 추가하는 마이그레이션은 예외 매핑 분기 점검을 세트로 한다** (anti-patterns.md "DB 컬럼 변경 시 3곳 한 세트" 규칙과 같은 성격)
  4. 제약 위반 → 에러코드 매핑은 **통합테스트로 검증**한다 (실제 위반을 유발해 응답 코드를 assert). 분기가 틀려도 컴파일·단위테스트는 통과한다

---

## 테스트

### FK 없는 DB의 삭제/캐스케이드 SQL은 실DB로 고아행 0건을 검증하라
- 출처: crew-solo-delete /verify (2026-06-12)
- 사례: deleteCrewWithAssociations(9테이블 네이티브 삭제)를 단위테스트에서 `verify(port).deleteCrewWithAssociations(id)` mock 호출 검증으로만 확인 → 실제 SQL 정합 미검증. step4가 "삭제 후 참조행 0건 assert"를 명시했는데도 mock으로 축소됨.
- 왜?: FK 제약이 없는 DB라 SQL 테이블명 오타·누락·순서 오류가 있어도 에러 없이 고아행만 남고 단위테스트는 그린이다. mock verify는 "호출됐다"만 보장할 뿐 "올바르게 지웠다"를 보장하지 않는다.
- 규칙: 삭제/캐스케이드/정리 경로는 TestContainers(integration 프로파일)로 실제 삭제를 실행하고 참조 자식 테이블 행 0건(또는 SET NULL)을 count로 assert한다. mock verify로 대체 금지. (write-test.md 3-6 참조)

### 단락평가로 호출이 사라지면 해당 테스트 stub도 같이 정리하라
- 출처: crew-solo-delete /simplify (2026-06-12)
- 사례: existsByCrewId 조회를 ACTIVE일 때만 호출하도록 단락평가로 바꾼 뒤, RECRUITING/COMPLETED 테스트의 given-stub이 남아 MockitoExtension STRICT_STUBS의 UnnecessaryStubbingException 발생
- 왜?: 서비스 최적화로 특정 분기에서 메서드 호출이 사라졌는데 테스트의 기존 stub을 함께 정리하지 않았다. 최적화와 테스트 정리는 항상 쌍이다.
- 규칙: 호출 경로를 줄이는 최적화(단락평가 등)를 넣으면 그 호출에 의존하던 테스트 stub을 같이 점검·제거한다

---

## 보안

### 외부 입력 URL은 반드시 Service에서 검증
- 출처: user-profile SDD (2026-04-14)
- 사례: 프로필 이미지 URL을 검증 없이 DB에 저장할 뻔함
- 왜?: "presigned URL로 업로드했으니까 당연히 우리 S3 URL이겠지"라고 가정함. 악의적 사용자가 임의 URL을 보낼 수 있다는 점을 고려 안 한 것.
- 규칙: 사용자가 입력한 URL은 Controller가 아닌 Service에서 도메인(S3 버킷) + 경로(본인 폴더) 검증 필수

### null 가드는 양쪽 다 넣어라
- 출처: user-profile PR 리뷰 (2026-04-14)
- 사례: syncAppleProfile엔 email != null 가드 있는데 syncKakaoProfile엔 없음
- 왜?: Apple 로그인 구현할 때 "email이 없을 수 있다"는 걸 알고 가드를 넣었지만, 카카오는 "항상 email을 준다"고 가정함. 실제로는 카카오도 email 미제공 케이스 존재.
- 규칙: 같은 역할의 메서드가 여러 개면 (카카오/애플) 방어 로직도 동일하게 적용

---

## FE

### dispose 시 비동기 작업 취소
- 출처: user-profile SDD (2026-04-14)
- 사례: EditProfileScreen에서 업로드 중 화면 나가면 CancelToken 미취소
- 왜?: "업로드가 빨라서 화면 나가기 전에 끝날 거"라고 가정함. 네트워크 느린 환경이나 큰 파일 업로드 시 문제 발생 가능.
- 규칙: 비동기 작업(API 호출, 업로드)이 있는 화면은 dispose에서 반드시 cancel 처리

### 상태 이중 관리 금지
- 출처: user-profile SDD (2026-04-14)
- 사례: mypage_screen에서 _user + authUserProvider 중복 상태
- 왜?: Provider에서 가져온 데이터를 로컬 변수에 복사해서 가공하려 함. 두 곳의 데이터가 달라질 수 있다는 점을 고려 안 한 것.
- 규칙: 같은 데이터를 로컬 변수와 Provider 양쪽에서 관리하지 않기. 하나만 선택

---

## 추가 방법

실수 발견 시 아래 형식으로 추가:

```markdown
### 교훈 제목
- 출처: 어디서 발견됐는지 (SDD명, PR 리뷰, 버그 수정 등)
- 사례: 구체적으로 뭘 잘못했는지
- 왜?: 왜 이런 실수가 생겼는지 (근본 원인 분석)
- 규칙: 다음부터 어떻게 해야 하는지
```
