# 추후 고려 사항

> 개발 중 나온 개선 아이디어나 스케일업 시 필요한 작업을 기록한다.
> 지금은 불필요하지만, 나중에 참고할 내용들.
> **최신 항목이 위에 오도록 추가한다.**

---

### [2026-08-05] 배포 헬스체크가 게이트로 작동하지 않는다 — 항상 초록

- 현재 상태: `.github/workflows/deploy.yml:169-171` 이 `sleep 15` → `curl -f .../actuator/health` → `docker image prune -f` 순이다. **두 군데가 동시에 깨져 있다.** ① 기동 실측이 **22.65초**라 15초 대기로는 curl 이 매번 실패한다. ② 실패해도 뒤따르는 `prune` 이 성공하면서 스크립트의 exit code 를 덮어써 **잡은 항상 success** 로 뜬다.
- 실측: 2026-08-05 릴리스 배포(run `31020388444`) 로그에 `curl: (56) Recv failure: Connection reset by peer` 가 찍혔는데 `deploy-backend → success`. 앱 자체는 정상이었다(외부 헬스 200 / 러너 3종 보정 완료) — 게이트가 통과시킨 게 아니라 **게이트가 아무것도 안 본 것**이다.
- 📌 주소(`localhost`)는 문제가 아니다: 그 `curl` 은 ssh-action 으로 **EC2 안에서** 돌고 `-p 8080:8080` 으로 컨테이너가 물려 있다. 에러가 `Connection refused` 가 아니라 `reset` 인 게 근거 — 포트는 열렸고 앱만 아직 안 뜬 상태다. 도메인으로 바꾸면 앞단 nginx·DNS·TLS 가 끼어 **방금 배포한 그 컨테이너를 봤는지**가 흐려진다. 도메인 확인은 게이트가 아니라 **배포 후 스모크**로 분리한다.
- 필요 시점: **다음 배포 파이프라인 작업 때 함께.** 그 전까지 배포 결과를 판단할 땐 초록불을 믿지 말고 CloudWatch `/triagain/app` 기동 로그와 `https://triagain.kr/actuator/health` 를 직접 본다.
- 이유: 수정 자체는 재시도 루프 3~4줄이지만 `deploy.yml` 은 tier-policy **Tier 3**(배포 경로 직결)이라 단독 PR + 사용자 승인이 필요하다. 지금 프로덕션이 정상이라 긴급도가 없어 분리한다.

> 📌 아래 3건은 PR #126(checkstyle 서프레션 해체) 리뷰에서 나왔다. 전부 코드 의미를 바꾸는
> 수정이라 그 PR의 바이트코드 동일성 증명을 깨뜨려 이관했다.
> (같이 이관됐던 "메서드 길이 30줄 정의 불일치"는 PR #130에서 해결돼 지웠다.
> 확정된 규칙은 `CLAUDE.md` 의 "메소드 길이" 항목에 있다.
> tabify 의 주석 속 `"""` 함정도 감지 가드가 들어가 지웠다 —
> 함정의 내용과 죽이는 이유는 `scripts/tabify.py` docstring 에 있다.)

### [2026-07-31] 초대코드 생성이 `Math.random()` — `SecureRandom` 전환 검토

- 현재 상태: `Crew.generateInviteCode()`가 `Math.random()`으로 31자 중 6자를 뽑는다(31⁶ ≈ 8.9억). `Math.random()`은 48비트 LCG인 `java.util.Random` 하나를 공유한다.
- 필요 시점: 비공개 크루가 늘어 초대코드가 실질적 접근 통제 수단이 될 때
- 이유: LCG는 출력을 충분히 모으면 이후 값을 예측할 여지가 있다. Phase 1 규모에서 실효 피해가 작고 전환은 인스턴스 교체 수준이라 미룬다.

### [2026-07-31] `reviews.report_id`에 유니크 제약이 없는데 단건 반환

- 현재 상태: `ReviewJpaRepository.findByReportId`가 `Optional`을 반환하는데, 마이그레이션 전수 확인 결과 `reviews.report_id`에 유니크 제약이 없다.
- 필요 시점: 한 신고에 리뷰가 2건 이상 생길 수 있는 흐름이 생길 때 (재심사 등)
- 이유: "신고당 리뷰 1건"이 코드로만 지켜지고 DB로는 안 지켜진다. 깨지면 `IncorrectResultSizeDataAccessException`으로 조회가 실패한다. 유니크 제약을 걸지 `List` 반환으로 바꿀지는 도메인 결정이 먼저다.

### [2026-07-31] `NotificationTargetQueryAdapter`의 다중 조인 네이티브 쿼리 3개 → MyBatis 이관 검토

- 현재 상태: `findReminderTargets` 등 3개가 네이티브 SQL을 `EntityManager`로 직접 실행하고 결과를 수동 매핑한다.
- 필요 시점: 이 쿼리들을 수정할 일이 생길 때 (그 PR에서 함께)
- 이유: 기술 선택("JPA=CRUD/쓰기, MyBatis=복잡한 조회")의 경계 밖이지만 동작에 문제가 없고, 이관은 XML 매퍼 신설을 수반한다. 위 [2026-06-12] `CrewJpaAdapter` 항목과 같은 성격이라 함께 볼 것.

### [2026-07-26] 부하서버 GC를 Serial → G1으로 전환할지 (측정 완료, 전환 보류)

- 현재 상태: 부하서버 JVM = **Serial GC** (1GiB 에르고노믹스). 2026-07-13/14 Serial↔G1 통제쌍 실험 완료 — 8블록(2밤 × 2전략 PESS/COND × 2암 게이트 on/off). 정본: `load-test/results/0714/serial-vs-g1/` 통합비교 4쌍.
- 결정 (2026-07-26, Issue #97 종결): **측정만 하고 전환 보류.** G1은 런중 Major GC 오염을 소멸시키나(전략 교차 2/2 확정), 체감 지연인 **완료 p95는 Serial ≈ G1**으로 밤간 노이즈 봉투 안(판정 유보) → 전환을 정당화할 개선이 안 보임.
- 필요 시점: 부하가 커져 런중 Major GC 오염이 실제 p95를 흔드는 규모에 도달하거나, 힙을 키워 Serial STW가 문제되는 시점. 그때 재평가.
- 근거 상세: `load-test/results/0714/serial-vs-g1/Serial-vs-G1_통합비교-4쌍.md` §7 — ①오염 소멸=확정, ④기저 p95 우열=유보, ⑤커널 SYN drop=GC 독립, ⑥할당 불변식 0.46~0.51MB/도달 8/8.

### [2026-07-11] 솔로 인증 마감 검증을 사이클-끝 → 슬롯별 마감으로 정렬 (deadlineTime FE 노출 시)

- 현재 상태: `CreateHabitVerificationService`가 마감 검증(텍스트 122행·사진 107행)에 `cycle.getDeadline()`(= `startDate + 3일` at deadlineTime, **사이클 끝**)을 쓴다. 스케줄러 `findExpiredWithoutVerification`는 **슬롯별 마감**(`start_date + completed_days` at deadlineTime + 5분)으로 실패 판정 → 검증-수락 경계와 스케줄러-실패 경계의 기준이 다르다. 그날 슬롯 마감을 넘긴 인증이 스케줄러가 사이클을 FAILED로 처리하기 전(~5분 창) 수락될 수 있다.
- 발현 조건(둘 다 필요): (1) **커스텀 deadlineTime** — 기본 23:59:59는 grace가 자정을 넘겨 D12 슬롯 가드가 날짜 경계에서 거부하므로 안전. `POST /habits`(`CreateHabitRequest.deadlineTime`)는 커스텀 값을 수용하나 **v1 FE는 미노출**. (2) 스케줄러 `fixedDelay=5분` 창.
- 결정 (2026-07-11, PR#94 Codex P1 검토): **수용 + 이연 (옵션 B)**. 솔로 = 자기 스트릭만 영향(타 유저 무관), 발현 조건 좁음, **crew도 동일 패턴**(`FindOrCreateActiveChallengeService` 사이클-끝 마감 + `FailExpiredChallengesScheduler` 슬롯별 SQL — 회귀 아님). 현 시점 조치 없음.
- 필요 시점: **deadlineTime을 FE에 노출**하거나(정상 유저 도달 경로 생김) 커스텀 마감 습관을 정식 지원하는 시점. 그때 재평가.
- 근본 해결: 검증 경로(107·122행)를 `(cycle.getStartDate() + cycle.getCompletedDays()).atTime(habit.getDeadlineTime())` 슬롯별 마감으로 교체 → 검증-수락 경계 == 스케줄러-실패 경계 불변식. habit 도메인 인증 로직 변경 = Tier 3 (SDD step1 §3 갱신 + 실패-선커밋 테스트). crew 동반 여부는 별도 판단. 상세: `triagain/docs/fix-instructions/06-pr94-codex-리뷰-2건.md` Issue 2.

### [2026-06-17] 크루 첫 인증 알림 fan-out: 배치 발송 + Dead Letter 큐 도입

- 현재 상태: `VerificationNotificationAdapter.sendCrewFirstVerificationNotification`이 수신자 목록을 루프로 순회하며 수신자별 try/catch로 격리 발송. 발송 실패 건은 로그만 남기고 재시도 없음.
- 필요 시점: 크루 규모가 커지거나 FCM 일시 장애 시 재시도 필요성이 생기는 시점
- 이유: Phase 1(2~10명 소규모 크루, TPS 50)에서는 동기 루프 + 실패 로그로 충분. Phase 2 이상에서는 (1) FCM Batch API로 1회 호출 전환하여 N회 왕복 제거, (2) 실패 건을 `dead_letters` 테이블에 적재 후 `DeadLetterProcessor`로 재시도, (3) `notificationExecutor` 풀 크기를 트래픽 기반으로 재조정하는 방향으로 개선한다.

### [2026-06-17] 크루 첫 인증 알림 멱등 가드: DB 쿼리 → 인메모리 캐시 전환 검토

- 현재 상태: `existsCrewFirstVerificationOnDate`가 매 fan-out 진입마다 `notifications` 테이블 JPQL 쿼리를 실행. Phase 1 규모(소규모 크루, 낮은 TPS)에서는 문제 없음.
- 필요 시점: 크루 수가 수천 개를 넘어 스케줄러/동시 첫인증 요청이 DB에 집중되는 시점
- 이유: 동일 `(crewId, targetDate)` 쌍에 대해 첫 인증 직후 수 초 이내 중복 이벤트가 들어올 수 있고, 이 시간 내 동시 쿼리가 모두 `false`를 반환해 복수 fan-out이 트리거될 수 있다. 근본 해결은 Redis SET NX + TTL(당일 23:59 만료)로 원자적 중복 방지를 적용하는 것. Option 2(DB 쿼리 가드)의 한계임을 인지하고 캐시 도입 시 이 항목을 재검토한다.

### [2026-06-12] FK-safe 크루 삭제 SQL이 CrewJpaAdapter / UserCrewMembershipAdapter 2곳 중복 → 공유 추출 필요

- 현재 상태: `CrewJpaAdapter.deleteCrewWithAssociations`(크루 삭제 경로)와 `UserCrewMembershipAdapter.deleteCrewWithAllData`(회원탈퇴 경로)에 leaf→root 삭제 SQL이 중복. FK 구조 변경 시 두 곳을 함께 수정해야 한다.
- 필요 시점: FK 구조 변경, 또는 삭제 경로 추가 시
- 이유: 이번 범위에서 공유 추출을 하려면 withdrawal 어댑터(`UserCrewMembershipAdapter`)까지 수정해야 해 범위를 초과. 복제를 허용하되 기록으로 남긴다.
- 추가 미처리: FK-safe 크루 삭제가 `dead_letters`(target_id=crewId, CREW_ACTIVATE/CREW_COMPLETE 타입)는 정리하지 않음 — `deleteCrewWithAssociations`/`deleteCrewWithAllData` 공유 추출 시 함께 처리 검토(자동 재시도 스케줄러 없어 기능 영향은 없음).
- 추가 (2026-07-31, PR #126 리뷰): 두 곳 모두 **네이티브 SQL을 `EntityManager`로 직접 실행**한다는 지적도 나왔다. 공유 추출을 할 때 "한 곳으로 모으기"와 "JPA 리포지토리/MyBatis로 옮기기"를 같이 볼 것 — 위 [2026-07-31] `NotificationTargetQueryAdapter` 항목과 같은 성격이다.

### [2026-06-11] permitAll 공개 경로 목록이 SecurityConfig/DevSecurityConfig에 중복 — 공유 상수 추출 후보

- 현재 상태: 공개 경로 matcher 8줄이 `SecurityConfig`(prod)와 `DevSecurityConfig`(!prod)에 동일하게 중복. 공개 경로 추가 때마다 두 파일을 짝으로 수정해야 하며, 한쪽 누락 시 해당 프로필에서만 401이 나는 비대칭 버그가 됨 (feedback-link PR AI 셀프리뷰에서 지적). 추가로 permitAll 동작 자체를 검증하는 자동 테스트가 없어 한쪽 누락을 CI가 못 잡음 (현재는 수동 curl로 검증).
- 필요 시점: 공개 경로가 다음에 또 추가될 때 (그 PR에서 함께 처리)
- 이유: `PUBLIC_PATHS` 공유 상수 추출은 작은 리팩토링이지만, feedback-link PR은 지시서가 "기존 보안 설정 변경 금지 — 항목만 추가"라 범위 외였음. 추출 시 permitAll 검증 테스트(공개 경로 무토큰 200/302, 보호 경로 401) 추가도 함께 검토.

### [2026-06-11] 문의/건의는 `/feedback` → 외부 구글폼 302로 수집 — 인앱 피드백 도메인 미도입

- 현재 상태: `GET /feedback`(공개, permitAll 단일 경로)이 `application.yml`의 `triagain.feedback-form-url`로 302 리다이렉트. 앱은 고정 URL(`triagain.kr/feedback`)만 가리키고, 폼 교체는 설정값 변경 + 재배포로 끝(앱 릴리스 불필요). 응답은 구글 시트에서 수동 확인.
- 필요 시점: 피드백 유입량 증가 시
- 이유: 인앱 피드백 도메인(테이블·입력 폼·이메일 인프라)은 수십 명 규모 출시 앱에 오버엔지니어링. 유입 늘면 인앱 도메인 도입 재검토 (중복 설계 방지용 기록 — sdd/feedback-link)

### [2026-06-10] crews 날짜 역전 방지 — DB CHECK 제약 추가 보류 (데이터 정리 후 별도)

- 현재 상태: `home-crew-tabs` SDD에서 "사부작"(03.18~03.02, 종료일<시작일) 같은 역전 데이터를 일회성 정리하기로 함. 재발 방지용 `CHECK (end_date > start_date)`(crews) Flyway 마이그레이션은 이번 라운드에서 **추가하지 않음**(option A로 데이터 작업을 /implement와 분리).
- 필요 시점: 위반 데이터 정리(진단 0건) 완료 직후 별도 PR. 시드/직접 insert로 역전 데이터가 재유입되면 우선순위↑.
- 이유: ① CHECK 마이그레이션은 적용 시 기존 전 행을 검사 → 위반 행이 남아 있으면 `ADD CONSTRAINT` 실패로 부팅/배포가 깨짐(정리 0건 선행 필수). ② `/implement` 자동 루프는 삭제 vs 보정 결정에서 못 멈춰, 데이터 정리/CHECK를 자동 run에 넣으면 위험. ③ 기능(탭/성취)과 무관한 순수 재발 방지라 미뤄도 화면 영향 0. (범위는 `end_date > start_date`만; 최소 +6일은 레거시 호환 위해 앱 `validateDates`로만 유지.)

### [2026-06-10] 홈 완료 크루 성취 표시 — 달성률(%) 지표 보류 (분모 정의 미합의)

- 현재 상태: `home-crew-tabs` SDD에서 완료 크루 카드 성취 표시는 양수 지표(`successCount` 작심삼일 N회, `verifiedDayCount` 총 N일 인증)만 노출하기로 확정. 달성률(%)은 이번 범위에서 제외.
- 필요 시점: 완료 크루에 "비율형" 성취를 보여줄 니즈가 생기거나 성취 프레이밍을 강화할 때
- 이유: 달성률의 분모 정의가 갈린다 — (a) 전체 챌린지 사이클 수 대비 SUCCESS 비율, (b) 크루 전체 기간 일수 대비 인증 일수, (c) 가입~종료 사이 인증 가능일 대비 등. 특히 가입만 하고 인증 0인 유저는 어떤 분모로도 0%가 되어 "작심삼일도 괜찮아" 컨셉과 충돌(실패 박제). 분모·표기 정책(0% 비노출 여부 포함)을 먼저 합의한 뒤 추가해야 안전. v1은 양수 지표만으로 동기부여한다. (성취 0회 크루는 성취 라인 자체를 미렌더링 — SDD step1 §4)

### [2026-06-09] 혼자 크루장 크루 삭제 — 게이트가 "크루 전체 기준"이라 유령 멤버 기록 시 솔로 리더가 삭제 불가

- 현재 상태: `crew-solo-delete` SDD에서 ACTIVE 솔로 크루 삭제 게이트를 **crew_id 기준**(`challenges`에 해당 crew_id 레코드가 1건이라도 있으면 거부, `CR026`)으로 확정. 리더 user_id 기준이 아님.
- 한계(의도된 트레이드오프): 멤버 B가 인증(challenge 생성) 후 **회원탈퇴**하면, `WithdrawUserService`가 B의 challenge를 ENDED로 두고 행만 남긴 채(`endActiveChallenges`) crew_member에서 제거(`removeMember`)해 크루가 솔로화된다. 이때 리더 본인은 인증 전이어도 B의 ENDED challenge가 crew_id를 단 채 남아 있어 `existsByCrewId`가 true → **솔로 리더가 자기 크루를 삭제하지 못한다**(CR026). 정 지우려면 회원탈퇴(통째 하드삭제) 경로를 타야 함 — 이 기능이 줄이려던 "크루 하나 지우려고 계정 삭제" 비대칭이 이 드문 케이스에 한해 재현된다.
- 필요 시점: 위 케이스 문의가 실제 접수되거나, 회원탈퇴한 멤버의 잔존 인증 기록 보호 가치를 재평가할 때
- 이유: ① 발생 조건이 다중 겹침(2명↑ 크루 → 멤버가 인증까지 → 그 멤버 계정 탈퇴 → 리더는 미인증)이라 수십 명 규모에선 거의 안 나옴. ② "삭제 허용 후 기록 동반 삭제(crew 기준 + FK-safe로 정리)" 대안보다 "차단(현행)"이 데이터 파괴 위험이 없어 안전. 대안 전환 시엔 떠난 멤버의 인증 기록을 함께 하드삭제하게 되므로 정책 판단 필요. ③ 회원탈퇴 시 비-리더 멤버의 challenge/verification 행을 ENDED만 하고 남기는 현 동작 자체를 "탈퇴 시 정리"로 바꾸면 이 한계가 근본 해소되나, 별도 과제(탈퇴 데이터 정리 정책)로 분리.

### [2026-06-08] 배포/CI 안정성 — Docker 빌드가 매 빌드마다 gradle 배포판을 라이브 다운로드

- 현재 상태: `Dockerfile` builder 스테이지가 `RUN ./gradlew bootJar`로 빌드하는데, gradle wrapper가 매 빌드마다 `services.gradle.org`에서 `gradle-8.12-bin.zip`을 다운로드한다(타임아웃 10초, 레이어 캐시 없음). CI의 E2E job도 동일하게 wrapper로 gradle을 받는다.
- 필요 시점: gradle CDN 장애로 배포/CI가 재차 실패하거나, 무인 배포 신뢰성이 중요해질 때 (Phase 2 전 권장)
- 이유: 2026-06-08 invite-landing 운영 배포(#70) 시 `services.gradle.org` 일시 장애로 deploy-backend가 2회 연속 실패(SocketTimeout 10초, `org.gradle.wrapper.Install.forceFetch`)했고, 같은 날 E2E도 504로 1회 실패. CDN 회복 후 3차 재실행으로 배포 성공. **코드와 무관한 외부 인프라 의존성이 배포 성공률을 좌우**한다. 개선 옵션: (1) gradle 배포판을 Docker 레이어/`--mount=type=cache`로 캐시, (2) `gradle:8.12-jdk17` 베이스 이미지로 wrapper 다운로드 자체 제거, (3) `gradle-wrapper.properties`의 `networkTimeout`을 10s→60s로 완화, (4) 미러 `distributionUrl` 사용. Phase 1에선 재실행으로 우회 가능하나 근본 제거 권장.

### [2026-04-09] 스케줄러 윈도우 + 보정 이중 구조 (부하 분산)

- 현재 상태: `FailExpiredChallengesScheduler` / `ExpireUploadSessionScheduler` 모두 5분마다 전량 스캔으로 단순화 (Phase 1, 500명 규모 기준 안전). 별도 startup compensation runner도 동일 스케줄러 메서드를 호출.
- 필요 시점: Phase 2 또는 challenges/upload_sessions 누적량이 만 단위에 도달 시
- 이유: 윈도우 조회 구조는 한 틱(GC pause/배포/지연)을 놓치면 영구 미판정 위험이 있어 PR 리뷰(DOM-C2, 2026-04-09)에서 제거. 부하 분산 목적의 윈도우+보정 이중 구조는 멀티 인스턴스 스케줄러 조정, 분산 락(`DistributedLockPort`), 미처리 추적 테이블과 함께 재설계해야 안전하다. Phase 1 규모에서는 단순 전량 스캔이 더 견고.

### [2026-04-09] DeadlinePolicy LocalTime 비교 → LocalDateTime/Clock 리팩토링

- 현재 상태: `DeadlinePolicy.todayDeadline(LocalTime)`이 `LocalDate.now().atTime(time)`으로 오늘의 deadline을 만든다. `isWithinDeadline`은 LocalDateTime 비교지만 deadline 값 자체가 LocalTime에서 파생되어 자정 wrap에 취약하다. `LocalTime.now().minusMinutes(N)`으로 "과거 시각"을 만들려는 단위테스트가 자정 직후 00:00 ~ 00:1X 영역에서 wrap되어 어제 23:5X로 잘못 해석되고 비결정적으로 실패한다.
- 영향 받은 테스트: `FindOrCreateActiveChallengeServiceTest.deadlineTimeExceeded_throws`, `CreateUploadSessionServiceTest.noActiveChallengeAfterCrewDeadline_throws` — BE-P1-2 PR에서 `Assumptions.assumeTrue` 가드로 임시 회피 (자정 ~ 00:15 사이엔 skip)
- 필요 시점: Phase 2 또는 production 측에서 실제 자정 경계 버그가 보고되는 시점
- 이유: production 코드가 deadline을 LocalTime → LocalDateTime으로 변환하는 순간 "오늘 날짜 + 그 시각"이라는 가정이 들어가는데, 자정 직후 호출 시 어제의 deadline을 오늘로 해석하거나 그 반대로 해석할 가능성이 있다. 해결 옵션: (1) deadline을 처음부터 LocalDateTime으로 들고 다닌다, (2) `Clock`을 DI해서 테스트에서 고정 시각 주입, (3) 도메인 정책을 "challenge.startDate + completedDays + deadlineTime"으로 상시 LocalDateTime 계산
- 우선순위: 자정 직후 트래픽 거의 없는 Phase 1에서는 실서비스 영향 매우 낮음. 테스트만 임시 회피해두고 Phase 2에서 LocalDateTime 기반 리팩토링과 함께 처리

---

### [2026-04-08] Apple revoke 후 트랜잭션 실패 시 정합성 트레이드오프

- 현재 상태: `WithdrawUserService`가 트랜잭션 밖에서 Apple `/auth/revoke` 호출 후 트랜잭션 진입. revoke 성공 직후 `completeWithdraw()` 트랜잭션이 실패하면 → Apple 측은 revoke 완료 / DB는 active 상태로 inconsistency 발생
- 필요 시점: 회원탈퇴 신뢰성 이슈 보고 시
- 이유: 사용자가 재로그인하면 신규 토큰으로 정상 동작하므로 사용자 경험상 거의 무영향(이전 refresh_token은 어차피 revoke되어 무효). 양방향 보상 트랜잭션을 도입하면 복잡도가 급증하고 Phase 1에는 과도. 현재는 graceful 정책으로 두고, 운영 중 실제 사례 발생 시 재평가

---

### [2026-04-08] ~~users.apple_refresh_token DB 평문 저장 → application-level 암호화~~ ✅ RESOLVED 2026-04-09

- ~~현재 상태: V16 마이그레이션으로 `apple_refresh_token VARCHAR(500) NULL` 평문 저장~~
- **해결**: SEC-C1 PR 리뷰 대응으로 AES-256-GCM AttributeConverter(`AesGcmStringConverter`) 적용 + V17로 컬럼 1024 확장. 키는 `APPLE_REFRESH_KEY` 환경변수 (GitHub Actions Secrets). KMS / Secrets Manager 승급은 Phase 2 후속 과제로 유지.

---

### [2026-04-08] APPLE_PRIVATE_KEY 환경변수 노출 표면 → Secrets Manager / 파일 마운트

- 현재 상태: GitHub Secrets → SSH `envs:` → `docker run -e APPLE_PRIVATE_KEY=...`로 PEM 전체가 컨테이너 환경변수에 주입. `/proc/<pid>/environ`을 통해 동일 EC2 내 동일 권한 사용자가 접근 가능
- 필요 시점: Phase 2 또는 다중 사용자/다중 컨테이너 환경 전환 시
- 이유: 현재 EC2는 단독 사용자/단일 컨테이너이므로 실질 위험 낮음. 개선 옵션: (1) AWS Secrets Manager에서 런타임 fetch, (2) `.p8` 파일을 권한 제한 디렉토리(예: `/etc/triagain/keys/apple.p8`, mode 0400)에 두고 `APPLE_PRIVATE_KEY_PATH`만 환경변수로 주입

---

### [2026-03-27] BC 경계 위반 리팩토링 (D-C1, D-C2) — D-C1만 부분 해결

- ~~D-C1: UserCrewMembershipAdapter (User Context)가 Crew Context의 JPA 인프라를 직접 import~~ ✅ **2026-04-09 해결 (최소 침습)**: 어댑터를 `crew.infra.adapter.UserCrewMembershipAdapter`로 이동. `CrewMembershipPort`는 `user.port.out`에 그대로 두고 Crew BC가 구현하는 형태(다른 BC가 Output Port 구현 — 헥사고날 정당 패턴). **권장 옵션**(`UserWithdrawnEvent` 발행 → 각 BC 자체 정리)는 후속 PR로 분리.
- 현재 상태:
  - D-C2: NotificationAdapter, VerificationNotificationAdapter가 Support Context의 Notification 도메인 모델을 직접 생성
- 필요 시점: Phase 2 또는 마이크로서비스 분리 시
- 이유: 모노리스 단일 배포이므로 Phase 1에서는 실질적 문제 없음. 리팩토링 범위가 크고 기능 변경 없으므로 별도 PR로 분리

---

### [2026-03-27] 부하 테스트 우선순위

- 현재 상태: Phase 1 (500명, TPS 50 목표), 부하 테스트 미실시
- 필요 시점: Phase 1 출시 전 (1~3번), 데이터 축적 후 (4~5번)
- 우선순위:

| 순위 | 대상 | 핵심 이유 | 테스트 시나리오 |
|------|------|----------|---------------|
| 1 | `POST /verifications` | 트랜잭션 내 FCM 동기 호출 + 마감 직전 피크 몰림 | 마감 10분 전, 50명 동시 인증 |
| 2 | `GET /crews/{crewId}/feed` | 가장 빈번한 조회 + 복합 데이터 조합 | 100명이 각자 다른 크루 피드 동시 조회 |
| 3 | `POST /crews/join` | 비관적 락 경합 + 정원 동시성 정합성 검증 | 정원 10명 크루에 20명 동시 가입 → 10명만 성공? |
| 4 | 리마인더 스케줄러 (`findReminderTargets`) | 4-way JOIN (crews→crew_members→verifications→users) 풀스캔 가능성 | 크루 100개 × 멤버 10명 상태에서 쿼리 시간 측정 |
| 5 | `GET /crews` | 홈 화면 진입 = 전원 동시 호출 + todayVerified 배치 쿼리 검증 | 200명 동시 홈 화면 진입 |

---

### [2026-03-27] CreateVerificationService — 트랜잭션 내 FCM 동기 호출 분리

- 현재 상태: `CreateVerificationService.createVerification()`이 `@Transactional` 내부에서 `verificationNotificationPort.sendChallengeSuccessNotification()`을 동기 호출. 이 안에서 FCM 발송(`FcmAdapter.send()`)이 `@Retryable` 3회(1s+2s+4s, 최악 7초) 블로킹되며, 그동안 DB 커넥션을 점유
- 필요 시점: Phase 2 또는 트래픽 증가 시
- 이유: CLAUDE.md Anti-Pattern "트랜잭션 안에 외부 API 호출 금지" 위반. Phase 1(TPS 50, 500명)에서는 커넥션 풀 고갈 가능성 낮으나, 트래픽 증가 시 인증 API 응답 지연 + 커넥션 풀 고갈 위험. 해결 방향: (1) `@Async` + 스레드 풀로 FCM 비동기 분리, (2) 트랜잭션 커밋 후 이벤트(`@TransactionalEventListener`)로 FCM 발송, (3) 스케줄러처럼 트랜잭션 밖으로 FCM 호출 이동
- 검증: 리팩토링 전후로 `POST /verifications` 부하 테스트 실시하여 응답 시간 및 커넥션 풀 사용량 비교 필요

---

### [2026-03-27] BC 경계 위반 리팩토링 (D-C1, D-C2) — 중복 항목, 위 [2026-03-27]로 통합됨

- 위 동일 일자 항목 참조 (D-C1는 2026-04-09 해결)
- D-C2만 잔여: NotificationAdapter, VerificationNotificationAdapter가 Support Context의 Notification 도메인 모델을 직접 생성

---

### [2026-03-25] Dead Letter 자동 재시도 스케줄러

- 현재 상태: DeadLetter 도메인에 `retry()`, `resolve()` 메서드 구현 완료. DeadLetterRepositoryPort에 `findRetryable()` 메서드 존재. 재시도 스케줄러는 미구현
- 필요 시점: Phase 2 또는 Dead Letter 건수 증가 시
- 이유: Phase 1에서는 실패 건이 적고 수동 모니터링으로 충분. 지수 백오프(10분, 20분, 40분) 로직은 도메인에 구현되어 있어, 스케줄러만 추가하면 됨

---

### [2026-03-20] 알림 테이블 정리 스케줄러 (30일 삭제)

- 현재 상태: NotificationRepositoryPort.deleteOlderThan() 메서드는 구현 완료, 호출하는 스케줄러는 미구현
- 필요 시점: 알림 테이블 10만건 이상 시
- 이유: Phase 1 규모(500명)에서 알림 데이터량이 적어 즉시 도입 불필요. 데이터 증가 시 도입 예정

---

### [2026-03-20] CloudWatch 로그 연동

- 현재 상태: EC2 서버 로컬 로그만 존재
- 필요 시점: 출시 후 운영 모니터링 시
- 참고: 청천님 사례 — 중요 로그만 CloudWatch에 기록

---

### [2026-03-19] CrewPreviewAssembler 도메인 검증 로직 중복 해소

- 현재 상태: `Crew.addMember()`와 `CrewPreviewAssembler.calculateJoinBlockedReason()`이 동일한 가입 검증(정원/상태/마감일/중복)을 각각 수행. Assembler는 reason을 세분화(CREW_ENDED, LATE_JOIN_NOT_ALLOWED)하므로 단순 위임 불가
- 필요 시점: Phase 2 또는 다음 Crew 도메인 리팩토링 시
- 이유: 현재 두 로직은 동기화되어 있고, `addMember()` 수정 시 Assembler도 함께 확인하면 됨. 도메인 모델 변경 범위가 크므로 별도 작업으로 분리
- Phase 2 방향: `Crew` 도메인에 `getJoinBlockedReason(): Optional<JoinBlockedReason>` 메서드 추가 → Assembler는 위임만 수행

---

### [2026-03-15] Docker 이미지 SHA 태그 기반 배포

- 현재 상태: deploy.yml에서 `devjian/triagain:latest`로 pull/run. 빌드 시 SHA 태그(`devjian/triagain:${{ github.sha }}`)도 push하지만 배포에는 미사용
- 필요 시점: 롤백 필요성 발생 시 또는 Phase 2
- 이유: latest 태그 배포는 간단하지만 롤백 시 어떤 버전인지 추적 불가. SHA 태그로 배포하면 특정 커밋으로 즉시 롤백 가능

---

### [2026-03-13] Moderation Context BC 경계 위반 수정

- 현재 상태: `moderation/infra/CrewClientAdapter.java`가 `crew.domain.model.Crew`, `crew.domain.model.CrewMember`, `crew.port.out.CrewRepositoryPort`를 직접 import. Verification → Crew 경계 수정과 동일 패턴의 위반
- 필요 시점: 다음 Moderation Context 관련 작업 시
- 이유: 이번 PR은 Verification → Crew 경계만 수정 범위. Moderation도 동일하게 `CrewQueryUseCase` (Input Port) 도입 후 어댑터가 UseCase만 의존하도록 변경 필요

---

### [2026-03-13] Request DTO Validation 메시지 통일

- 현재 상태: `CreateUploadSessionRequest`에만 `@NotBlank(message = "...")` 설정. `CreateCrewRequest`, `JoinCrewRequest` 등 다른 Request DTO는 message 미설정 (기본 메시지 사용)
- 필요 시점: 프론트 에러 메시지 표시 구현 시
- 이유: 기능 문제는 아니고 일관성 이슈. 프론트에서 validation 에러 메시지를 유저에게 직접 표시하게 되면 한국어 메시지 통일이 필요

---

### [2026-03-11] 썸네일 생성은 Phase 2로 보류

- 현재 상태: 클라이언트 압축 이미지 1장만 업로드. 썸네일 미생성. COMPLETED = "원본 1장 업로드 완료"
- 필요 시점: Phase 2 (피드 성능 최적화 시)
- 이유: 지금 도입하면 아래 결정이 추가로 필요하여 업로드 플로우 안정화가 지연됨
  - 썸네일 생성 완료까지를 업로드 완료로 볼지
  - thumbnailUrl 저장 위치 (upload_session? verification?)
  - 피드/상세 응답 분기 방법
  - 썸네일 생성 실패 시 fallback 처리
- Phase 2 확장 방향:
  - thumbnailUrl 필드 추가 (피드: 썸네일, 상세: 원본)
  - 현재 imageUrl 중심 구조에서 thumbnailUrl만 추가하면 큰 변경 없이 확장 가능

---

### [2026-03-10] ~~코드 버그: 크루 최소 기간 미검증 (Crew.validateDates)~~ → 해결 완료 (2026-03-12)

- ~~현재 상태: `Crew.validateDates()`에서 `endDate > startDate`만 체크. biz-logic.md의 "최소 시작일+6일 (작심삼일 2회 보장)" 규칙이 코드에 미반영~~
- **해결**: `Crew.validateDates()`에 `endDate.isBefore(startDate.plusDays(6))` 검증 추가. 단위테스트(경계값+실패) 포함.

---

### [2026-03-12] 크루 최소 인원: 백엔드 @Min(1) 유지, 프론트에서 @Min(2) 제한

- 현재 상태: CreateCrewRequest @Min(1), Crew.java maxMembers < 1. biz-logic.md 규칙은 "2~10명"
- 필요 시점: 프론트 크루 생성 UI 구현 시
- 이유: 백엔드는 솔로 테스트 및 향후 솔로 모드 확장을 위해 @Min(1) 유지. 프론트 UI에서 최소 2명 제한으로 정상 사용자 가드. API 직접 호출로 1명 크루 생성 가능하나 Phase 1 규모에서 실질적 위험 낮음

---

### [2026-03-04] StartupCompensationRunner — Phase 2 전환 시 제거 검토

- 현재 상태: 단일 서버 + Spring @Scheduled 기반이라 서버 다운 시 스케줄러 미실행 → 서버 재시작 시 밀린 작업(크루 활성화 → 챌린지 실패 → 크루 종료)을 순서대로 보정
- 필요 시점: Phase 2 (Quartz 등 persistent scheduler 도입 시)
- 이유: Quartz의 misfire policy가 자동 보정을 제공하므로 이 Runner 제거 가능. 단, 제거 전에 3단계 순서(활성화 → 실패 → 종료) 보장 여부 확인 필요

---

### [2026-03-04 21:50] 챌린지 Lazy 생성 — 실패 후 미재도전 유저 알림

- 현재 상태: 챌린지 생성을 Eager(크루 활성화/참여 시) → Lazy(첫 인증 시 자동 생성)로 변경. 스케줄러는 FAILED 처리만 수행, 새 챌린지 자동 생성 제거.
- 필요 시점: Phase 2 (알림 시스템 도입 시)
- 이유: Lazy 생성이므로 실패 후 재도전하지 않는 유저는 챌린지가 없는 상태로 남음. 리마인더 푸시("다시 도전해보세요!")가 필요하지만 Phase 1에서는 알림 시스템 미구현.

---

### [2026-03-04 20:10] Apple 로그인 실제 연동 TODO

- 현재 상태: 코드 구현 완료 (Port/Adapter/UseCase/Controller/테스트), Cucumber @ignore + AdapterTest @Disabled
- 필요 시점: 앱스토어 출시 전
- 남은 작업:
  - Apple Developer 계정에서 Service ID 발급 → APPLE_CLIENT_ID 환경변수 설정
  - 실제 Apple Identity Token으로 E2E 검증
  - Cucumber @ignore / AdapterTest @Disabled 해제
  - Flutter 클라이언트 Apple Sign In 연동
- 이유: 백엔드 코드는 준비 완료, Apple Developer 계정 설정 + 클라이언트 연동이 별도 작업

---

### [2026-03-03 18:00] Logout 토큰 블랙리스트 도입

- 현재 상태: `POST /auth/logout`은 서버 no-op (200 반환만), 클라이언트가 로컬 토큰 삭제로 로그아웃 처리. refreshToken은 순수 JWT stateless.
- 필요 시점: Phase 2 (Redis 도입 이후)
- 이유: Phase 1에서는 Redis 미사용, 토큰 탈취 시나리오 대응은 Phase 2 보안 강화 시점에 적합
- Phase 2 계획:
  - `token_blacklist` 테이블 또는 Redis SET으로 블랙리스트 관리
  - `TokenBlacklistPort` (Output Port) + `RedisTokenBlacklistAdapter` 구현
  - `RefreshTokenService.refresh()` 시 블랙리스트 조회 추가
  - 만료된 블랙리스트 항목 정리 스케줄러 추가
  - `LogoutUseCase` 생성하여 블랙리스트 등록 로직 분리

---

### [2026-03-03 11:14] 예외 핸들러 로그 폭주 대비

- 맥락: GlobalExceptionHandler에 전체 핸들러 request URI 로깅 추가
- 지금 한 것: 모든 예외 발생 시 `[POST /auth/signup]` 형태로 요청 정보 로깅
- 추후 고려: TPS가 올라가면 동일 에러가 초당 수백 건 반복될 수 있음 (예: 봇 공격)
  - Rate-limiting 로깅 또는 Sampling 적용 검토
  - Phase 1 (TPS 50)에선 해당 없음, Phase 2 이후 트래픽 증가 시 재검토

---

### [2026-03-03 11:12] `/internal/**` Lambda 인증 필터 추가 필요

- 맥락: `/internal/**` 엔드포인트가 `permitAll`로 열려있어 보안 위험 → prod에서 `denyAll`로 임시 차단
- 지금 한 것: `SecurityConfig`(prod)에서 `denyAll`로 변경, `DevSecurityConfig`(!prod)는 `permitAll` 유지
- 추후 고려: Lambda 연동 시 시크릿 키 헤더 검증 필터 추가
  - Lambda 요청에 `X-Internal-Secret` 등의 헤더를 포함하고, Spring Security 필터에서 검증
  - 필터 추가 후 dev/prod 설정 통일 (`denyAll` → 필터 기반 인증으로 전환)
  - VPC 내부 통신만 허용하는 네트워크 레벨 제한도 병행 검토
