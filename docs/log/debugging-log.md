# 디버깅 & AI 협업 로그

> 기록 기준: 버그 수정 / 설계 판단 / AI 방향 수정 시에만 기록한다.
> 형식은 CLAUDE.md의 "디버깅 & AI 협업 로그 기록 규칙"을 따른다.

---

### [2026-08-03] tabify 주석 함정 차단 — 트리거 지난 추후고려 항목이 실은 미확인 사고였다

- 상황: `future-considerations.md`의 `[2026-07-31] scripts/tabify.py` 항목이 "필요 시점: **`src/test`에 tabify를 다시 돌리기 전**"이라 적혀 있었는데, 그 실행이 이미 `4acf044`(PR #133, 08-01)로 끝나 있었다. 문구 정리가 아니라 사고 조사가 먼저였다. 조사 결과 **피해 0** — 당시 부분 가드(`b8ae0ae`)가 이미 들어가 있었고, `src/test`의 유일한 텍스트블록 보유 파일 `AppleOAuthAdapterTest.java`의 본문이 보존돼 있었다. 다만 구멍은 살아 있었다: `convert()`가 `"""`를 볼 때마다 `inside`를 토글만 해서 **주석 안 `"""`가 짝수 개면 무경고로 통과**하고 텍스트블록 본문이 탭화된다. 스페이스 4가 탭 1이 되며 상대 들여쓰기가 압축돼 **문자열 값이 실제로 바뀐다**(`javac`로 컴파일해 확인)
- 내 판단: (1) 항목을 다시 쓰지 않고 **막고 지웠다** — 다시 쓰면 이월이 반복되고, 그게 PR #126에서 걷어낸 4개월 방치 패턴이다. 삭제는 PR #131(`272c08f`) 전례대로. (2) 렉서를 짜지 않고 **감지 후 SystemExit** — 오탐 방향이 "멀쩡한 파일에서 죽는 것"이라 조용히 변조되는 반대 방향보다 낫다. (3) **회귀 테스트 기각** — 저장소에 파이썬 테스트가 0개라 하네스를 새로 깔아야 하는데, 대상은 마이그레이션이 끝나 CI·gradle·hook 어디서도 호출되지 않는 일회성 도구다
- AI 역할: 노트의 "SQL이 조용히 바뀐다"는 주장을 그대로 믿지 않고 컴파일해 값까지 확인했다. **CodeRabbit의 "블록 주석은 못 잡는다" 지적을 재현했더니 "잡힌다"가 나와 오탐으로 닫을 뻔했다** — 왜 잡혔는지 파고드니 내 가드가 아니라 무관한 옛 가드(주석 `"""`가 홀수면 "블록이 안 닫혔다")가 우연히 걸러낸 것이었고, 짝수로 다시 만드니 뚫렸다. 지적이 사실이어서 반영(`f66bf39`)
- 배운 점: **"막혔다"는 "내가 막았다"를 증명하지 않는다.** 검증이 통과했을 때 *왜* 통과했는지 한 문장으로 설명할 수 없으면 아직 안 끝난 것이다. 그리고 미래형 메모("~하기 전에 X 할 것")는 **시제부터** 확인해야 한다 — 트리거가 지났다면 낡은 문구가 아니라 아직 안 읽은 사고 보고서다

---

### [2026-07-23] 만료 챌린지 스케줄러 lost update — 조건부 UPDATE로 교체 (D14-b)

- 상황: `FailExpiredChallengesScheduler`가 `findExpiredWithoutVerification()`을 **청크 트랜잭션 밖**(`:42`)에서 조회한 뒤, 그 스냅샷으로 `challenge.fail(); save(challenge)`를 수행(`:49-52`). `ChallengeJpaEntity`에 `@Version`도 `@DynamicUpdate`도 없어 `save()`가 전 컬럼 UPDATE라, 조회~저장 사이에 유저가 인증을 커밋하면 낡은 `completed_days` + `FAILED`로 덮어써 **유저의 성공이 실패로 뒤집힌다**. 예외도 DeadLetter도 없고 로그엔 "성공 1건"으로 남아 조용히 데이터가 틀어진다. 인증 창(마감+grace 5분)과 스케줄러 창(마감+5분 초과)이 밀리초 단위로만 겹쳐 발생 확률이 낮았을 뿐, 구조적으로 열려 있었다. 인증 취소·수정(SDD verification-cancel)이 들어오면 같은 행에 쓰는 주체가 3개가 되므로 선행 수정
- 내 판단: (1) **크루 참여 전략 C 선례를 그대로 따랐다** — `JoinCrewService:102-105` + `CrewJpaRepository.incrementMembersIfNotFull`처럼 도메인 메서드(`challenge.fail()`)는 유지하고 경합 조건만 DB predicate로 위임(`failIfUnchanged(id, expectedCompletedDays)`). `fail()`을 지우면 상태 전이 규칙이 SQL로 새어 헥사고날 경계가 무너진다. (2) `affected == 0`은 **오류가 아니라 정상 스킵** — 예외를 던지면 rehydrate 재시도 → 또 0 → DeadLetter 오염이라 기각. (3) **알림 대상 보정**: `ChunkProcessor.processor`가 `Consumer`라 스킵 건도 `successItems`에 포함되므로, 스킵 여부를 `Map<id, Boolean>`에 기록해 알림 대상에서 필터. 공용 엔진(`ChunkProcessor`, 스케줄러 6곳 공유)은 무변경. 알림은 현재 비활성이지만 "on/off UI 구현 후 복원" TODO가 있어 복원 시 터질 것을 지금 닫았다. (4) `@Version` 추가는 기각 — 조건부 UPDATE가 이미 compare-and-set이라 중복이고 다른 경로에 파급
- AI 역할: 오케스트레이터가 지시서를 실제 코드로 그라운딩(`ChunkProcessor` rehydrator 발동 조건·엔티티 어노테이션 부재·컬럼명 schema.md 대조)하고 sonnet이 구현. **사용자가 "`skippedIds.remove()` 분기가 실제로 도달하냐"고 물은 것이 설계 결함을 잡아냈다** — 추적해보니 도달 경로는 있었고(같은 청크의 *다른* 항목이 예외를 던지면 0행이던 건도 함께 재시도됨), 그 경로에서 기대치를 **rehydrate된 fresh 인스턴스**에서 읽고 있어 "DB 값 vs DB 값" 비교로 compare-and-set이 무효화되는 상태였다. 원본 스냅샷 맵으로 기대치를 고정해 수정
- 배운 점: **compare-and-set의 기대값은 "경합을 감지하려는 그 시점의 스냅샷"에서 와야 한다.** 값을 어디서 읽는지가 곧 감지 창의 정의다 — 재시도 경로에서 무심코 최신값을 읽으면 가드가 문법적으로는 멀쩡한 채 의미만 사라진다(테스트도 통과한다). 그리고 이 구멍은 수정 전 코드의 재시도 경로에도 이미 있었다: 조회 결과를 여러 트랜잭션에 걸쳐 처리하는 배치는 "재시도 시 무엇을 다시 읽고 무엇을 고정할지"를 명시적으로 정해야 한다

---

### [2026-06-13] FCM 키 스모크 테스트 엔드포인트 추가 (POST /internal/fcm-test)

- 상황: 2026-06-12 Firebase 서비스계정 키 무효(401)로 FCM 전멸. 발송 4경로가 전부 cron/이벤트/비활성이라 다음 cron까지 장애 탐지 불가. 키 로테이션·배포 직후 즉시 검증할 단건 스모크 엔드포인트 필요
- 내 판단: (1) 컨트롤러를 `@ConditionalOnProperty(firebase.enabled=true)`로 게이팅 — dev/test는 NoOp 어댑터가 항상 true 반환이라 게이팅 없으면 "거짓 성공", 그 환경에선 404로 오탐 차단. (2) `NotificationSendPort.send`의 3분기(true=성공 / false=토큰 영구무효 / BusinessException=발송실패 3회 재시도 후 throw)를 SUCCESS/TOKEN_INVALID/ERROR로 매핑. (3) SecurityConfig 무수정 — `/internal/**`는 InternalApiKeyFilter가 prefix로 자동 가드. (4) 전체발송 아닌 단건 fcmToken만 받아 위험 최소화(Tier 2 유지)
- AI 역할: fable이 지시서를 실제 코드와 그라운딩 검증(포트 시그니처·게이팅·시큐리티·테스트 패턴 4건 교정), sonnet이 3계층+단위테스트 구현, checkstyle import 그룹 규칙(java/org/com.triagain/catch-all/naver, option=top) 위반 교정
- 배운 점: NoOp 폴백이 있는 기능의 스모크 엔드포인트는 폴백 환경에서 비활성(404)으로 둬야 "성공" 신호가 진짜 성공을 의미한다

---

### [2026-04-09] develop→main PR 리뷰 Critical 3건 일괄 수정 + BC 어댑터 클래스명 충돌

- 상황: PR 리뷰에서 SEC-C1(Apple refresh_token 평문), DOM-C1(User→Crew BC 위반), DOM-C2(스케줄러 5분 윈도우 누락) 3건이 머지 차단으로 식별. C2 수정 중 어댑터를 `crew.infra.adapter.CrewMembershipAdapter`로 옮겼더니 기존 `verification.infra.CrewMembershipAdapter`(Verification → Crew 위임용 다른 어댑터)와 단순 클래스명 충돌 → Spring `ConflictingBeanDefinitionException`로 컨텍스트 로딩 실패
- 내 판단: (1) C1은 GitHub Actions Secrets 패턴(JWT_SECRET과 동일)으로 환경변수 주입, KMS 승급은 Phase 2로 분리. (2) C2는 어댑터를 `crew.infra.adapter`로 이동하는 최소 침습 — 권장된 `UserWithdrawnEvent` 이벤트 기반 분리는 후속 PR. 클래스명은 `UserCrewMembershipAdapter`로 되돌려 충돌 회피 (패키지로 BC 소속을 표현, 클래스명으로 용도 구분). (3) C3는 윈도우 제거 후 전량 스캔으로 회귀하면서 `compensateAllExpired*()` 메서드와 `@Scheduled` 메서드가 동일 동작이 되어 통합. 윈도우+보정 이중 구조는 future-considerations에 후속 과제로 기록 (Phase 2 분산 락과 함께 재설계 필요)
- AI 역할: 3개 Critical 묶음 플랜 작성 → 코드 수정 → 테스트/문서 동기화. 클래스명 충돌은 빌드 실패 후 grep으로 기존 동명 어댑터 발견 → 즉시 rename
- 배운 점: 한 BC 안에서 같은 클래스명을 다른 용도로 쓰지 않도록 사전에 grep으로 충돌 검사. 패키지가 다르더라도 Spring `@Component`/`@Repository`의 기본 빈 이름은 클래스명 기반이라 충돌함

---

### [2026-04-09] Stack PR base 미전환으로 PR #45/#46이 develop에 도달 못 한 사고

- 상황: BE-P1-1(PR #45)과 BE-P1-3(PR #46)을 stack PR로 만들었음. PR #45 base = `feat/crew-min-duration`(=PR #44 head), PR #46 base = `feat/active-crew-leave`(=PR #45 head). PR #44 머지 직후 PR #45/#46을 차례로 squash-merge → develop에는 PR #47만 추가됨. 다음날 release PR 만들려고 develop log를 보니 #45/#46이 아예 없음. production에 BE-P1-1(CR025), BE-P1-3(빈 크루 정리)가 빠진 상태로 진행돼 있었음
- 내 판단:
  1. **GitHub squash merge는 stack 부모 PR이 머지돼도 자식 PR의 base를 자동 전환하지 않는다.** PR #45를 stack 부모(`feat/crew-min-duration`)에 머지하면 그 squash 커밋은 부모 브랜치 안에서만 살아있고 develop에는 안 들어감. PR #46도 동일
  2. **복구는 cherry-pick이 가장 안전.** 두 squash 커밋(`39b5da9`, `ed4d579`)은 develop의 #47과 변경 영역이 disjoint해서 conflict 없이 cherry-pick 가능. 새 브랜치 `recovery/be-p1-1-and-p1-3`를 develop에서 분기 → 두 커밋 cherry-pick → recovery PR(#48) → rebase merge로 PR boundary 보존
  3. **rebase merge 선택 이유**: squash merge로 합치면 PR #45/#46 두 변경의 commit boundary가 사라져 history 추적이 어려워짐. rebase는 두 cherry-pick 커밋을 그대로 추가
  4. **향후 stack PR 작성 시**: 부모 PR 머지 전에 자식 PR의 base를 develop으로 직접 변경하거나, 부모 머지 직후 자식 base를 develop으로 갱신해야 함. GitHub UI의 "Edit base branch" 기능 사용
- AI 역할: develop log와 PR #45/#46 base 비교로 누락 진단, 두 squash 커밋의 parent chain 분석으로 cherry-pick 가능성 확인, recovery PR 생성/CI 모니터링/머지까지 일괄 수행
- 배운 점: stack PR은 GitHub 네이티브 기능이 약하다. graphite 같은 툴을 안 쓰면 base 자동 전환이 없어 사고 위험이 큼. 다음부터는 (a) stack 만들지 말고 순차 PR로 가거나 (b) 부모 머지 직후 자식 base를 즉시 develop으로 갱신하는 절차를 박아둘 것

---

### [2026-04-08] Apple Sign-In Token Revoke 구현 — refresh_token 일회용 관리

- 상황: App Store Review Guideline 5.1.1(v) 대응으로 Apple 회원탈퇴 시 `/auth/revoke` 호출 필요. revoke에 필요한 Apple refresh_token이 DB에 저장되지 않은 상태였음
- 내 판단:
  1. **refresh_token은 "탈퇴 시 1회용"으로만 취급** — Apple 권장(24시간마다 갱신)을 따르지 않음. 우리는 자체 JWT로 세션 관리하므로 Apple refresh_token으로 사용자 정보를 주기적으로 가져올 일이 없음. 갱신 로직 없으면 코드 단순화
  2. **Apple Client Secret JWT는 매 호출마다 즉석 생성** — 캐싱 안 함. ES256 서명은 빠르고, exp=5분으로 짧게. 캐싱 = 만료 처리 + 재생성 로직 + 동시성 = 복잡도 증가
  3. **revoke 실패는 graceful** — App Store는 "성실한 시도"를 요구하므로 호출 자체가 핵심. 실패 시 WARN만 남기고 탈퇴 진행
  4. **기존 Apple 사용자는 다음 로그인 시 backfill** — 강제 재로그인 미사용. backfill 전에 탈퇴하는 사용자는 어쩔 수 없는 것으로 수용 (사용자가 직접 Apple ID 설정에서 해제 가능)
  5. **`/auth/apple-signup`에는 authorizationCode 필수** — refresh_token 없이 가입하면 향후 탈퇴 revoke 불가. 가입 시점에 차단하는 게 안전
  6. **`/auth/apple` backfill에는 authorizationCode 옵셔널** — 기존 사용자 부담을 줄이고 best-effort로
- AI 역할: WithdrawUserService/AppleSignupService/AppleLoginService 흐름 분석, biz-logic.md/api-spec.md/schema.md 정본 갱신, Doc-First 실행 순서로 정리
- 배운 점: Apple OAuth는 다른 OAuth 제공자와 달리 client_secret이 고정 문자열이 아니라 매번 직접 서명한 JWT라는 점이 핵심 차이. 그래서 Team ID + Key ID + .p8 private key 인프라 셋업이 필요함

---

### [2026-03-20] 알림 인프라 설계 — 스케줄러 장애 격리 + 인앱 API

- 상황: FCM 푸시 + 인앱 알림 기능 구현. 스케줄러에서 한 건 실패 시 전체 중단 방지 필요
- 내 판단: @Transactional 대신 TransactionTemplate으로 건별 트랜잭션 격리. unread-count를 별도 UseCase로 분리하지 않고 GetNotificationsUseCase에 포함 (오버엔지니어링 방지). 30일 삭제 스케줄러는 Phase 1 규모에서 불필요하여 스킵
- AI 역할: 3계층(UseCase/Service/Controller) 구현, TransactionTemplate 장애 격리 패턴 적용
- 배운 점: 스케줄러 내부에서 개별 트랜잭션을 써야 한 건 실패가 전체를 롤백하지 않는다

---

### [2026-03-17] CI용 E2E 테스트 인프라 구축 — 기존 Cucumber 인프라 재활용 결정

- 상황: CI 파이프라인에 배포 전 E2E 자동 테스트 추가 필요. 별도 TestContainers 구성 vs 기존 Cucumber 인프라 재활용 선택
- 내 판단:
  1. 기존 Cucumber 인프라(TestContainers, DatabaseCleanup, REST-Assured) 재활용 → 새 의존성 추가 없이 E2eTestBase 클래스만 신규 생성
  2. E2E 테스트에서 Flyway 비활성화(`spring.flyway.enabled=false`) — Cucumber와 같은 TestContainers DB를 공유할 때 Flyway 이력 충돌 방지. Hibernate `create-drop`이 스키마를 관리
  3. Gradle `e2eTest` 태스크를 `@Tag("e2e")` + `includeEngines('junit-jupiter')`로 분리 — Cucumber Suite Engine과 격리
- AI 역할: 기존 테스트 인프라 분석, Flyway 충돌 원인 식별 및 해결
- 배운 점: 같은 TestContainers를 공유하는 복수 Spring Context에서 Flyway 이력 충돌은 `spring.flyway.enabled=false`로 해결 가능. `create-drop`과 Flyway를 동시에 사용하면 Flyway는 실질적으로 무의미

---

### [2026-03-14] GitHub Actions 테스트 실패 — H2 호환성 + Cucumber 테스트 버그 3건

- 상황: feat/s3-lambda-presigned-url 브랜치 PR 후 GitHub Actions에서 8개 테스트 실패. V9 마이그레이션의 `ALTER COLUMN SET NOT NULL` + V1의 부분 인덱스(partial index) 구문이 H2에서 미지원
- 내 판단:
  1. `spring.flyway.enabled: false` + `ddl-auto: create-drop`으로 테스트 환경에서 Flyway 비활성화 (H2 호환성 문제 근본 해결)
  2. `CrewFeedSteps.사용자가_크루에_참여_중이다`에 `scenarioContext.setCrewId()` 누락 → UploadSession 요청 시 crewId=null → C001 오류
  3. `CrewJoinSteps.크루_종료일이_N일_남았다`가 API 경유로 크루 생성 → 최소 6일 검증 실패 → 리포지토리 직접 생성으로 변경
  4. Lambda 콜백 테스트 어댑터가 `/internal/upload-sessions/{id}/complete` 호출 → 실제 엔드포인트는 `/internal/upload-sessions/complete?imageKey=...` (URL 불일치)
- AI 역할: H2 오류 추적, 각 테스트 실패의 실제 원인 분석, 4개 버그 연쇄 수정
- 배운 점: "contextLoads 실패로 cascading"이라는 가정은 틀렸음. Cucumber 테스트는 별도 컨텍스트로 실제 비즈니스 오류를 드러낸다. 테스트 실패 원인은 반드시 각 실패 메시지를 직접 확인해야 함

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

---

### [2026-06-12] crew-solo-delete — hard delete·동시성·자동완료 설계 판단

- 상황: 솔로 크루(currentMembers==1) + 인증 전 삭제 기능 구현 중, hard delete vs soft delete, late-join 동시성 안전성, 자동완료 스케줄러와의 역할 중복 세 가지 설계 선택이 필요했음
- 내 판단:
  1. **hard delete 유지** — 코드베이스 삭제 컨벤션은 User만 soft delete(`deleted_at`, V15 마이그레이션 — 앱스토어 계정삭제 규정 + `reactivate` 재활성화 + JWT 토큰버전 무효화), 나머지(RECRUITING 크루 삭제·회원탈퇴 솔로크루·LeaveCrew 마지막 멤버, Challenge·Verification·CrewMember·Notification·Reaction)는 전부 hard delete. 솔로-삭제 게이트가 "솔로(currentMembers==1) + 인증 전(crew_id 기준 challenges 0건)"을 보장하므로 보존 가치가 없음. Crew에 soft delete 도입 시 모든 크루 쿼리에 `deleted_at IS NULL` 필터가 필요해 블래스트가 지나치게 커 오버엔지니어링. sweep(9-테이블 네이티브 정리)의 무게는 hard delete 선택이 아니라 DB에 FK 제약(캐스케이드)이 없어서임 — FK 제약 추가가 근본 단순화이며 별도 과제로 future-considerations에 기록
  2. **late-join ↔ 삭제 동시성 안전** — `DeleteCrewService`와 `JoinCrewService`/`JoinCrewByInviteCodeService` 모두 `findByIdWithLock`(`SELECT … FOR UPDATE`, PESSIMISTIC)으로 같은 crew 행의 비관적 쓰기 락을 경쟁 → DB 수준 직렬화. 가입이 먼저면 currentMembers=2 → 삭제가 `validateDeletable`에서 CR019(크루원 존재)로 거부. 삭제가 먼저면 가입이 행을 못 찾아 CREW_NOT_FOUND. "새 멤버를 깔고 삭제"·고아행이 구조적으로 불가능
  3. **자동완료 스케줄러로 대체 불가** — `CompleteExpiredCrewsScheduler`(매일 00:05)가 빈 ACTIVE 크루를 end_date 도달 시 COMPLETED로 전환하지만, 크루 기간이 7~30일 가변이라 최대 30일을 기다려야 함. 또한 COMPLETED 크루는 CR026으로 영구 삭제 불가. 실수로 만든 빈 크루의 즉시 삭제 탈출구로서 솔로-삭제가 필요
- AI 역할: 코드베이스 삭제 컨벤션 전수 조사(hard/soft delete 분포), 비관적 락 경쟁 시나리오 2가지 시뮬레이션, 자동완료 스케줄러 흐름 분석
- 배운 점: 삭제 전략은 "전체 컨벤션 일관성 + 실제 쿼리 블래스트"로 판단해야 한다. sweep 복잡도를 soft delete 탓으로 오해하기 쉽지만 실제 원인은 FK 제약 부재였고, 두 문제를 분리해서 봐야 올바른 결론에 이른다

---

### [2026-06-12] FCM 푸시 전면 실패 — 서비스계정 키 교체 후 Google OAuth 401

- 상황: `CrewStartNotificationScheduler`(cron 09:00)가 실행됐으나 `FcmAdapter.java:57`에서 HTTP 401 UNAUTHENTICATED로 3회 재시도 후 전면 실패. EC2에 배포된 Firebase 서비스계정 키(`triagain-firebase-service-account.json`, 6/10 02:46 교체분)가 무효였고 Google이 OAuth 액세스 토큰 발급을 거부했음. 코드 회귀 아님 — `FirebaseConfig` 마지막 변경 03-21, `deploy.yml` 04-14, 2달간 무변경
- 내 판단:
  1. **탐지 공백 3겹이 원인** — (a) 배포 게이트(`deploy.yml:120`)가 키 파일 존재 여부만 확인, 유효성 미검증 → 죽은 키여도 `FIREBASE_ENABLED=true`로 부팅. (b) `GoogleCredentials.fromStream()`이 JSON 구조만 파싱, Google 통신은 첫 `send()` 호출 시까지 지연(lazy) → 부팅 성공 = 키 정상이라는 착각 유발. (c) 스케줄러 요약 로그가 DB 저장 카운트를 FCM 발송 결과처럼 출력 → "전체=2건, 실패=0건" 거짓 초록불, FCM 실패는 별도 WARN으로만 기록됨
  2. **토큰 안전 확인** — 401은 `UNREGISTERED`/`INVALID_ARGUMENT`가 아니므로 `clearFcmToken` 미호출, 유저 FCM 토큰 보존됨. 키 복구 후 재등록 없이 발송 재개 가능
  3. **복구** — Firebase 콘솔에서 동일 서비스계정(`firebase-adminsdk-fbsvc@triagain-85536`) 신규 키 발급 → 죽은 키 `.dead-20260612` 백업 후 scp 교체 → 재배포(18:22 클린 부팅 확인). 단, 부팅 시 `send()` 미실행이라 발송 인증은 아직 미검증
  4. **후속 조치** — (a) FCM 스모크 테스트 엔드포인트 `/internal/fcm-test` 신설(Tier 2, BE 에이전트 지시 전달 완료). (b) 스케줄러 요약 로그를 FCM 발송 결과와 DB 저장 결과로 분리. (c) 배포 게이트 키 유효성 검증 보강 검토 중
- AI 역할: 3겹 탐지 공백(배포 게이트·`GoogleCredentials` lazy 검증·요약 로그 설계) 분석, FCM 에러코드(`UNAUTHENTICATED` vs `UNREGISTERED`) 구분으로 토큰 안전 여부 판정
- 배운 점: `GoogleCredentials.fromStream()`은 구조 파싱만 하고 Google과 실제 통신은 하지 않는다. 키 교체 후에는 반드시 온디맨드 `send()` 호출로 발송 인증까지 확인해야 한다. 스케줄러 요약 로그는 처리 대상 카운트와 외부 I/O 결과를 분리해서 집계해야 거짓 초록불을 막는다
