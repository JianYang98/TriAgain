# Redis REDIS_ASYNC Phase 3 구현 검증

[TIER 판정] Tier: 3 | 근거: DB transaction·멱등성·worker lifecycle·동시성 경계 | 판정자: orchestrator | 2026-10-01

## 기준과 구현 범위

- 구현 전 및 PR 준비 전 `git fetch origin develop`: `6b1372b01de0f08fa828b6f02cff115929d83959` (PR #181).
- 정본: orchestration 저장소 `sdd/redis-async-join-3/`의 step0, step1, step2, step4, redis-worker-contract 다섯 문서.
  문서의 작성 당시 구현 금지 문구는 이후 사용자의 명시적 구현·PR 승인으로 해제됐다.
- 구현: `pending → BLMOVE RIGHT LEFT → strict parse → DB INSERT/증가 transaction → COMMIT → exact raw LREM`.
- Phase 1·2·3 SDD와 Lua·준비 스크립트·HTTP 가입 Service·ErrorCode·DDL·운영 설정은 수정하지 않았다.
  BE 정본 다섯 문서에 실제 구현 상태와 실험 전제를 동기화했다.
- 미해결 Policy Blocking 없음. processing 잔존 startup guard·recovery·retry·reprocessing·새 health/admission 기능 없음.

## 관리 라이브러리와 경계

- Spring Boot 3.4.13, Spring Data Redis 3.4.13, Lettuce 6.4.2.RELEASE: Gradle runtimeClasspath dependencyInsight로 확인.
- 해당 Spring Data Redis JAR의 `LettuceListCommands.bLMove`는 `getAsyncDedicatedConnection`을 사용한다.
  `LettuceConnection.await`는 `LettuceFutures.awaitOrCancel(future, timeout, MILLISECONDS)`를 호출한다.
- block 1s, 기존 command 2s/connect 1s를 유지한다. 설정 검증은 command > block을 요구하며
  1s의 응답 여유를 유지하는 현행 값으로 실제 반복 nil 대기를 검증했다. 한 명령이 command timeout을 넘겨도 계속 동작한다는 뜻이 아니다.
- `CrewJoinPersistenceService.persist`가 TransactionTemplate을 소유하고 commit 뒤 반환한다.
  기존 transaction 안에서의 호출은 거부하여 바깥 commit 전에 ACK하는 참여 transaction을 방지한다.
- 정상 종료는 stop 요청을 먼저 기록하고 이미 gate에 들어간 유한 claim을 기다린 뒤 작업 스레드에 최대 10s join한다.
  진행 중 DB 작업은 interrupt하지 않는다. 한도 초과는 SHUTDOWN_INCOMPLETE 로그이며 강제 종료 시 완료를 보장하지 않는다.

## P3-T1~T12 대조와 실제 실행

새 테스트:

- [CrewJoinPendingWorkerTest](../../src/test/java/com/triagain/crew/application/CrewJoinPendingWorkerTest.java): 20건, 실패/skip 0.
- [RedisCrewJoinWorkerE2eTest](../../src/test/java/com/triagain/e2e/RedisCrewJoinWorkerE2eTest.java): 21건, 실패/skip 0.
- [CrewJoinRedisConfigurationTest](../../src/test/java/com/triagain/crew/infra/redis/CrewJoinRedisConfigurationTest.java): 9건, 실패/skip 0 (기존 테스트 보강).

| SDD | 실물 검증과 관측 | 결과 |
|---|---|---|
| P3-T1 | 실제 Lua로 first→second→third 승인 후 FIFO claim/ACK. 별도 공백·필드 순서가 다른 raw에서 processing 방향과 exact LREM 1→0 검증 | PASS |
| P3-T2 | 실제 block 1s/command 2s에서 nil 3회·총 2s 초과 생존, 이후 DB commit+ACK, 정상 종료 오류 로그 없음. 제어 가능한 claim/DB 작업 중 stop의 대기·다음 claim 없음. claim 전송 전/이동 후 응답 유실 모델 | PASS |
| P3-T3 | 실제 PostgreSQL INSERT 1, current_members +1, MEMBER/CRMB, 고정 confirmedAt. ACK 호출 시 transaction 비활성·이미 DB 반영됨, exact raw 제거 | PASS |
| P3-T4 | DB Service 직접 호출 1→0, count/기존 가입 시각/role 보존. 기존 LEADER에도 중복 호출로 role·시간 불변 | PASS |
| P3-T5 | 대상 pair 외 PK 충돌·varchar 초과·실제 잘못된 SQL 오류. 연결 실패는 Port 주입. 오류를 replay로 흡수하지 않고 미ACK·중단 | PASS |
| P3-T6 | 크루 부재/정원 불일치 UPDATE 0행: invariant 예외, INSERT rollback, raw 보존. CR002로 변환하지 않음 | PASS |
| P3-T7 | 실제 INSERT 후 UPDATE 오류, beforeCommit 오류로 확정 rollback. 멤버/인원 모두 rollback·ACK 0회·다음 pending 미claim. 별도 commit 응답 유실 모델은 DB commit을 남기되 미ACK | PASS |
| P3-T8 | ACK 전송 전 실패/raw 잔존, 실제 LREM 0행, LREM 실행 뒤 응답 유실/raw 제거 모델을 구별. DB 결과 유지·다음 claim/재시도 없음·직접 DB 재호출 0행 | PASS |
| P3-T9 | JSON/객체 여부/필드 누락·추가·중복·타입·blank·offset·밀리초·날짜·trailing JSON 검증. 실제 Redis malformed raw 보존·DB/ACK 없음·다음 pending 미claim | PASS |
| P3-T10 | REDIS_ASYNC 단일 worker 기동, DB 세 전략 Redis 없는 context 기동·worker/Redis health 미등록. 기존 HTTP health 회귀 포함. 잔여 작업 재기동 테스트 없음 | PASS |
| P3-T11 | 실제 HTTP 201 뒤 worker 전 DB 비멤버·내 목록 없음·상세 403, worker commit 뒤 목록/상세 membership·인원 2·원래 가입 시각 인식 | PASS |
| P3-T12 | 기존 Phase 2 API(9), Lua/Adapter(24), 초기화 Lua(19), 준비 스크립트(2), DB 전략 가입 unit·Cucumber·CONDITIONAL 동시성 E2E, 초대 거부·health/startup 회귀 | PASS (기존 skip 별도) |

주입한 장애 모델을 실제 전원 장애·TCP 응답 유실 재현으로 해석하지 않는다. 아래 미검증 범위를 함께 읽는다.

## 실행 명령과 XML 결과

환경: Java 17, PostgreSQL `postgres:16-alpine`, Redis `redis:7.4.11-alpine`, 기존 Testcontainers 사용.

| 명령 | 실제 결과 |
|---|---|
| `./gradlew test --tests '*JoinCrewServiceTest' --tests '*JoinCrewByInviteCodeServiceTest'` (구현 전 baseline) | PASS. 가입 unit 32 + Cucumber 발견 130 중 기존 skip 20 = 실제 142건 |
| `./gradlew test cleanE2eTest e2eTest checkstyleMain checkstyleTest compileJava compileTestJava` (변이 원복 후) | PASS. test 발견 783, skip 26, 실제 757, 실패/error 0. 당시 E2E 100/skip 0 |
| `./gradlew cleanE2eTest e2eTest checkstyleMain checkstyleTest compileJava compileTestJava dependencyInsight --dependency lettuce-core --configuration runtimeClasspath` (Lua FIFO·목록 조회 보강 후 최종) | PASS. E2E 101건, skip/실패/error 0. Checkstyle PASS |
| `./gradlew compileJava compileTestJava -x test dependencyInsight --dependency spring-data-redis --configuration runtimeClasspath` | PASS. 컴파일 및 Spring Data Redis 관리 버전 확인 |

`test`/`e2eTest`는 실제 실행된 task와 XML을 확인했다. 최종 집계는 test **757건 실행 + 기존 skip 26건**, E2E **101건 실행/skip 0건**이다.
기존 skip: `apple-login.feature` @ignore 5, `crew-activation.feature` @wip 6,
`challenge-auto-creation.feature` @wip 9, `AppleTokenVerifierAdapterTest` class @Disabled 6.
이번 변경은 skip을 추가하지 않았다. 새 Phase 3 테스트는 전부 실행했다.

개발 중 null 문자열을 raw 노출로 오판한 로그 단언 1건과 import 순서 위반을 수정했다. 최종 실행에는 해당 실패가 없다.

## 민감성 증명 — 세 변이 각각 FAIL → 원복 PASS

각 실행은 `./gradlew cleanE2eTest e2eTest --tests '*RedisCrewJoinWorkerE2eTest.<method>'`로 XML을 새로 생성했다.
컴파일 실패·0건·NPE가 아니라 아래 실제 단언 실패를 확인했고, 각 변이를 finally에서 복원한 뒤 같은 테스트를 다시 실행했다.

| 변이 위치·변경 | 대상 method | FAIL 관측 | 원복 |
|---|---|---|---|
| `CrewJoinPersistenceService.insertAndIncrement`: inserted==0에도 incrementMembersIfNotFull 호출 | `p3t4_duplicatePairPreservesCountRoleAndTime` | 1/1 실패, expected count 2 / actual 3 | 1/1 PASS |
| `CrewJoinPendingWorker.process`: persistence.persist 앞에 queue.ackRaw(raw) 추가 | `p3t7_failureAfterInsertRollsBackAndDoesNotAckOrClaimNext` | update/commit 두 경우 2/2 실패. expected processing=[raw] / actual=[] | 2/2 PASS |
| `CrewJoinPersistenceService.persist`: explicit joinedAt을 LocalDateTime.now()로 대체 | `p3t3_newMembershipCommitsBeforeExactAck` | 1/1 실패. expected 2026-01-02T03:04:05.678 / actual 2026-10-01T14:10:37.522020 | 1/1 PASS |

변이는 최종 코드에 남아 있지 않다. 재현용 명령·변경 줄과 단언을 위에 남겼고 로컬 원본 로그/XML은 저장소 밖 `~/Projects/triagain-evidence/redis-p3-evidence/`에 보존했다(커밋 대상 아님).

## SDD self-review

- SQL 충돌 대상은 `(crew_id,user_id)` 한정. JPA save 예외를 잡아 정상 replay로 바꾸는 코드 없음.
- INSERT·증가는 같은 transaction이고 0행 replay에는 증가 없음. UPDATE 0행은 invariant 실패다.
- application DB Service는 Redis/raw JSON을 모르고 worker는 전체 Crew를 load/save하지 않는다.
- ACK는 commit 성공 반환 후 원문 raw·count=1로만 실행. ACK 0행/결과 불명에서 processing 잔존을 단정하지 않는다.
- 정확히 세 String 필드·중복 key·시간 검사. 전역 ObjectMapper 설정을 바꾸지 않고 전용 ObjectReader로 검증한다.
- 종료/claim/parse/DB/ACK 경계 및 로그의 namespace/runId·실패 단계·원인을 대조했다. 정상 nil/종료는 오류 중단 로그가 아니다.
- Phase 2 생산 테스트는 context 시작 전 MockitoBean lifecycle 격리. 기존 pending-only·DB 불변 단언을 보존했다.
- W2 실험 전제: fixture 미래 startDate로 startup compensation 대상 제외. 다른 writer·날짜 경계 배제는 실험 절차이며 구조적 락 수정 없음.
- W3 실험 절차: STOPPED 로그 확인→신규 부하 중단→이미 보낸 요청 결과 확인→Redis/DB/로그 보존.
  pending 증가나 health UP을 worker 상태 판정으로 사용하지 않는다.
- BE 정본 다섯 문서와 구현의 201 의미·Phase 4 이관을 대조했다. 원래 가입 API 형태·Lua ABI·운영 기본 CONDITIONAL 유지.

## 미검증·지원 범위 밖

- 실제 프로세스 kill/전원 장애, 물리 TCP 단절·commit/ACK 응답 유실은 재현하지 않았다. 경계 주입 + 실제 DB/Queue 결과로 검증했다.
- shutdown join 10초 한도 초과에 대한 실제 느린 DB/강제 종료 실험은 실행하지 않았다. 정상 idle/claim/DB 진행 중 종료는 검증했다.
- Flyway 운영 마이그레이션 재적용은 수행하지 않았다. 기존 E2E는 create-drop/Entity UNIQUE이며 V22의 동일 컬럼 unique index는 읽기로 대조했다.
- 운영 부하·다중 worker·stale Crew 외부 writer 경합은 재현하지 않았다. 외부 writer는 승인된 실험 전제에서 배제한다.
- processing 잔존 재기동, startup recovery, retry/reprocessing, DLQ, Sentinel/Cluster, leave/rejoin, reconciliation은 Phase 4 이후이며 구현/검증하지 않았다.
- 기존 skip 26건은 이번 검증의 PASS에 포함하지 않는다. 원격 PR CI 상태는 PR에서 별도로 확인한다.

## 구현 중단 후 orchestrator 재검증 — 2026-10-01

- 구현 세션이 마지막 수정(`CrewJoinWorkQueueAdapter`) 직후 중단되어 최종 E2E XML이 그 수정보다 앞섰다. 최종 소스로 다시 실행했다.
- `./gradlew test e2eTest checkstyleMain checkstyleTest --rerun-tasks`: PASS. test 783건(실행 757 + 기존 skip 26), E2E 101건, 실패/error 0.
- 변이 재현: `CrewJoinPendingWorker.process`에서 `persistence.persist` 앞에 `queue.ackRaw(raw)` 추가 → `p3t7_failureAfterInsertRollsBackAndDoesNotAckOrClaimNext` 2/2 단언 실패(`RedisCrewJoinWorkerE2eTest.java:382`), 원복 후 2/2 PASS.
- `/simplify` 반영 2건: pending/processing key를 `CrewJoinRedisProperties.pendingKey()`·`processingKey()`로 모아 생산자·소비자 Adapter가 공유. 종료 대기 단위 테스트의 전체 스레드 스택 검색을 `stop()` 호출 스레드 상태 확인 helper로 교체. 반영 뒤 같은 명령에 `compileJava compileTestJava`를 더해 재실행 PASS(수치 동일).
- `/simplify` 미반영: 테스트 fixture·초기화 Lua 중복, E2E와 단위 테스트 중복, worker 전용 context 분리, timeout 검증 위치 이동, 단계 enum화. 동작 무변경 정리지만 이번 범위 밖이거나 SDD의 "새 설정 없음"과 충돌한다.


## PR #182 후속 수정·재검증 — 2026-10-02

### 실제 TCP 응답 유실과 수정

- 기준: PR HEAD `6c3a8971a7b438e33a135e13d949520bfd161550`, 최신 develop
  `6b1372b01de0f08fa828b6f02cff115929d83959`. 기존 미커밋 변경 없이 같은 BE worktree/branch에서 진행했다.
- `RedisReplyDropProxy`는 실제 Redis 7.4.11에 명령을 전달하고 서버의 응답 프레임을 완전히 받은 후
  클라이언트에는 전달하지 않고 TCP 연결을 닫는다. 재연결은 수락하여 Lettuce의 투명한 재전송을 관측한다.
- 수정 전 `CrewJoinTransportFailureIntegrationTest` 두 케이스는 모두 단언 FAIL:
  - BLMOVE 응답 유실: claim 3회(첫 A, 재전송 B, 다음 대기), ACK 1회. pending 비었고 processing에 A 잔류.
  - LREM 응답 유실: claim 1회, ACK 2회. pending에 B, processing은 비었음.
  - 둘 다 `expected: 1` 전송 횟수 단언에서 실패했다. 컴파일 오류·0건 실행 실패가 아니다.
- worker의 standalone 전용 private factory에 `autoReconnect=false`, `REJECT_COMMANDS`를 적용했다.
  기존 factory의 주소·DB·인증·TLS·command/connect timeout·ClientResources를 계승하며 producer bean을
  대체하지 않는다. 설정 검증도 factory의 실제 command timeout을 읽는다. Queue bean close에서 전용 factory만 해제한다.
- 수정 후 같은 두 테스트 PASS:
  - BLMOVE: claim 1회, ACK 0회, A processing / B pending, persistence 호출 0회, claim 오류 로그·worker 중단.
  - LREM: claim 1회, ACK 1회, A 제거 / B pending, persistence 호출 1회, ACK 오류 로그·worker 중단.
- **검증 분리:** 이 TCP 테스트의 persistence는 mock이다. DB commit·rollback·인원·joinedAt은 기존
  `RedisCrewJoinWorkerE2eTest` 21건의 실제 PostgreSQL 테스트로 검증했다. 네트워크 장애와 실 DB를 결합한
  하나의 테스트라고 주장하지 않는다. 앞 절의 "물리 TCP 단절 미재현"은 이 후속 Redis 응답 경계 두 건에 한해 갱신된다.

### 명령과 결과

| 명령/검증 | 실제 결과 |
|---|---|
| `./gradlew cleanE2eTest e2eTest --tests '*CrewJoinTransportFailureIntegrationTest'` | 수정 전 2/2 단언 FAIL → 수정 후 2/2 PASS |
| `./gradlew compileJava compileTestJava test cleanE2eTest e2eTest checkstyleMain checkstyleTest` | PASS. test 784건 중 기존 skip 26, 실행 758, 실패/error 0. E2E 103건 실행, skip/실패/error 0 |
| `CrewJoinWorkerRedisConnectionTest` | producer의 autoReconnect·접속 설정·수명 보존, worker 설정 분리·close 확인 PASS |
| `CrewJoinRedisConfigurationTest` | Boot RedisConnectionFactory 단일 bean·producer template 연결 보존, 세 DB 전략·relaxed 표기 격리 PASS |
| `git diff --check` | PASS |

Checkstyle의 테스트 주석 들여쓰기 2건을 수정한 뒤 위 전체 명령이 PASS했다. 마지막 전체 회귀 때의 모든
`src` 파일 해시를 저장했고, 아래 변이 원복 후 전체가 동일함을 확인했다. 테스트 실패용 변경은 남아 있지 않다.

### 현재 코드에서 다시 실행한 민감성 증명

각 행은 `./gradlew cleanE2eTest e2eTest --tests '*<class>.<method>'`로 변이 FAIL → 원복 PASS를 실행했다.

| 변이 | 대상 테스트 | 실패 단언 | 원복 |
|---|---|---|---|
| INSERT 0행에도 인원 증가 | `RedisCrewJoinWorkerE2eTest.p3t4_duplicatePairPreservesCountRoleAndTime` | 1/1 FAIL, 인원 expected 2 / actual 3 | 1/1 PASS |
| persist 전에 ACK 추가 | `RedisCrewJoinWorkerE2eTest.p3t7_failureAfterInsertRollsBackAndDoesNotAckOrClaimNext` | 2/2 FAIL, processing expected raw / actual empty | 2/2 PASS |
| joinedAt 대신 now() | `RedisCrewJoinWorkerE2eTest.p3t3_newMembershipCommitsBeforeExactAck` | 1/1 FAIL, 고정 승인 시각과 현재 시각 불일치 | 1/1 PASS |
| worker autoReconnect만 true로 복원 | `CrewJoinTransportFailureIntegrationTest.replyLost_stopsWithoutTransparentReplay` | 2/2 FAIL, claim expected 1 / actual 3, ACK expected 1 / actual 2 | 2/2 PASS |

### SDD 대조와 PR 댓글 처리

- step0 범위·Phase 4 제외, step1 자동 재명령 금지, step2 공개 계약, step4 P3-T1~T12,
  redis-worker-contract의 claim/ACK 결과 불명 중단과 대조했다. 새 정책 결정·Policy Blocking 없음.
- DB 멱등 Service·worker 루프·producer Lua/Adapter·운영 yml·DDL·ErrorCode는 이번 수정에서 변경하지 않았다.
- CodeRabbit 재전송 지적 반영. Greptile의 Given–When–Then 및 판단 로그 위치 지적 반영.
  BusinessException 일괄 전환은 하지 않으며 내부 실패 계약에 따른 판단을 debugging-log에 기록했다.
- 루트 Phase 1·2·3 SDD는 수정하지 않았다. BE 정본 다섯 문서는 실제 연결 동작에 맞춰 갱신했다.
- 미검증: 프로세스 kill·전원 장애, DB commit 응답의 실제 TCP 유실, 종료 10초 초과, TLS 실접속,
  운영 부하·multi-worker·Sentinel/Cluster·Phase 4 recovery/restart/retry. TLS는 설정 계승만 단위 테스트했다.
- 원격 CI 및 사용자 머지 승인은 PR에서 별도 확인한다. 이 기록은 머지 승인을 의미하지 않는다.
