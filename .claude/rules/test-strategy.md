---
description: 테스트 작성 시 참고하는 전략 (역할분담, 실행 축(@Tag), 단위테스트 규칙/범위, E2E, Cucumber 검증 흐름)
paths: "src/test/**/*.java, **/*.feature, build.gradle"
---

# 테스트 전략

## 역할 분담

- **쿠컴버 시나리오**: AI가 `step1-biz-logic.md`를 근거로 초안 작성 → **사람이 리뷰**
  (리뷰 관점: 이게 실제로 중요한 유저 여정인가 / 빠진 케이스는 없나)
- **단위테스트**: AI 작성 (비즈니스 규칙 검증)
- **E2E**: PR CI에서 자동 실행 (실행 조건은 아래 "무엇이 언제 도는가")

## 무엇이 언제 도는가 — 실행 축은 이름도 위치도 아니라 `@Tag`다

| 태스크 | 필터 (`build.gradle`) | 도는 것 |
|---|---|---|
| `./gradlew test` | `excludeTags 'e2e'` | e2e 태그 **없는** 전부 (단위 + Cucumber + integration 태그) |
| `./gradlew e2eTest` | `includeTags 'e2e'` + `includeEngines 'junit-jupiter'` | e2e 태그 붙은 전부. Cucumber 엔진은 빠진다 |

| 태그 | 효과 |
|---|---|
| `@Tag("e2e")` | `test`에서 **제외**된다 |
| `@Tag("integration")` | **없다.** `build.gradle`이 이 태그를 안 쓴다 — `test`에서 그냥 같이 돈다 |

⚠️ **축이 하나 더 있다 — Gherkin 태그는 위 `@Tag`(JUnit)와 별개다.**
`CucumberTest`의 `@ConfigurationParameter(FILTER_TAGS_PROPERTY_NAME, "not @wip and not @ignore")` 때문에
`.feature` 파일에 `@wip`·`@ignore`가 붙으면 **`test`에서 조용히 빠진다.** 실사용 3건 —
`crew-activation.feature`·`challenge-auto-creation.feature`(`@wip`) · `apple-login.feature`(`@ignore`).
**파일이 있어도 안 돈다.** 루트 `/verify` §1은 이 태그가 달린 시나리오를 API 레벨 테스트 게이트
**불충족**으로 본다(2026-08-19) — 쿠컴버로 게이트를 충족시키려면 태그가 없어야 한다.

⚠️ **태그는 `E2eTestBase` 상속으로도 전파되고, 그렇게 붙은 게 `e2e/` 밖에 더 많다.**
`e2eTest`가 도는 건 `e2e/`의 해피패스·동시성만이 아니라 아래 패키지의 `*IntegrationTest`들도 포함이다:

| 위치 | 태그가 붙는 경로 |
|---|---|
| `e2e/` | `E2eTestBase` 상속 |
| `crew/infra/` · `verification/application/` | 상속 + `@Tag("e2e")` 직접(중복이지만 무해) |
| `support/infra/` | **상속만** — 직접 태그가 없어 grep으로 세면 놓친다 |

→ **`./gradlew test` 그린은 "전부 통과"가 아니다.** 저 `*IntegrationTest`들은 `e2eTest`를 돌려야
비로소 돈다 — 하필 삭제 캐스케이드·챌린지 만료·슬롯 마감처럼 조용히 깨지면 데이터가 상하는 경로들이다.
엔티티·Request DTO·삭제 경로를 건드렸으면 `test`만 보고 커밋하지 마라.

**규모는 세지 말고 실행해서 확인한다** (`write-test.md` §2 의 같은 규칙):

```bash
./gradlew cleanE2eTest e2eTest && ls build/test-results/e2eTest/   # 무엇이 실제로 돌았나
grep -rln '@Tag("e2e")\|extends E2eTestBase' src/test/java/        # 정적 근사 (베이스 1건 포함)
```

**세는 명령을 두 번 틀렸다. 둘 다 같은 병이다 — 정적 grep이 실제 실행을 대신하지 못한다.**

- `@Tag("[a-z]*")` → `e2e`의 숫자 `2`가 안 잡혀 4건이 0건 (2026-08-06)
- `@Tag("e2e")` → **상속을 못 봐서** `ReactionEntityConstraintIntegrationTest`를 놓침 (2026-08-09)

**0건 실행 가드**: 태그가 전부 사라지면 `e2eTest`는 결과 XML도 없이 `BUILD SUCCESSFUL`을 낸다
(Gradle 8.12엔 `failOnNoDiscoveredTests`가 없다). `build.gradle`의 `e2eTest`에 0건이면 실패시키는
가드를 넣어뒀다 — **그 가드를 지우지 마라.** 지우면 CI의 `E2E Tests` 체크가 헛돌아도 초록불이다.

## 테스트 레벨 — 이름과 판별

| 접미사 | 레벨 | HTTP |
|---|---|---|
| `*Test` | 단위 — 도메인 모델 · 서비스 · 어댑터 | 안 탐 |
| `*IntegrationTest` | 실 DB(TestContainers) 서비스·제약 검증 | 안 탐 |
| `*ApiTest` | **MVC 스택 경유** — 라우팅 · 인증 헤더 · 직렬화 · 에러코드→HTTP status | **탐** |
| `*E2eTest` | 핵심 해피패스 전 구간 | 탐 |

**접미사와 태그는 별개 축이다.** 접미사는 "무엇을 검증하나", 태그는 "어느 태스크에서 도나".
`*IntegrationTest`인데 e2e 태그라 `test`에서 빠지는 조합이 실제로 5개 있다 (위 참조).

`*ApiTest`가 `/verify` §1 게이트("신규 엔드포인트마다 API 레벨 테스트 1개 이상")를 충족하는 레벨이다.
쿠컴버(`acceptance/`)와 E2E도 같은 게이트를 충족한다 — 셋 중 택1.

⚠️ **이름은 신호일 뿐 진실이 아니다.** 이름은 붙이는 사람이 정하므로 실제와 어긋날 수 있다
(2026-08-09 실측: `*IntegrationTest`는 하나도 HTTP를 안 탄다).
**어느 테스트가 HTTP를 타는지**는 이름이 아니라 이걸로 본다:

```bash
grep -rln "RANDOM_PORT\|webAppContextSetup\|AutoConfigureMockMvc" src/test/java/
```

`MockMvc`를 뺀 건 `standaloneSetup`이 걸리기 때문이다 — 컨텍스트를 안 띄워 인증 필터도
전역 예외 매핑도 안 타므로 게이트를 충족하지 못한다.

⚠️ **이 명령도 상속은 못 본다.** `E2eTestBase`가 `RANDOM_PORT`라 상속받은 5개 `*IntegrationTest`는
서버가 뜨는데도 안 잡힌다 (걔들은 호출을 안 해서 결론은 같다 — 2026-08-09 실측 0건).
호출 쪽을 grep으로 잡으려는 시도도 실패한다: `MockMvc`를 넣으면 `standaloneSetup`이 딸려오고,
빼면 `givenUser()`로 부르는 E2E가 빠진다. **믿을 만한 단일 grep은 없다.**

**엔드포인트별 충족 여부는 이 명령이 아니라 해당 `.feature`·`*ApiTest`를 직접 찾아 확인한다.**

**이름과 실제가 어긋나면 실제가 정본이고 이름을 고친다.**
레벨별 작성 방법은 `.claude/skills/write-test.md` §2 참조 (여기는 이름 규칙만, 방법은 그쪽이 정본).

## 단위테스트 규칙 (AI 필수 준수)

- 반드시 비즈니스 규칙을 검증해야 한다
- mock으로 의존성만 때리고 assertNotNull만 하는 테스트 금지
- 각 테스트명은 "~하면 ~한다" 형식으로 비즈니스 의도 명확히 표현
- BDD 스타일 (Given-When-Then) 주석으로 구조화
- 성공 케이스 + 예외 케이스(Unhappy Path) 반드시 1개 이상 포함
- 검증 예시:
  - IN_PROGRESS 챌린지가 user/crew당 1개인지
  - deadline + grace 이후에 인증이 거부되는지
  - FAILED 챌린지에 인증이 불가능한지
  - 정원 초과 시 가입이 거부되는지

## 단위테스트 범위

- 대상: 도메인 모델 + 서비스(커맨드) + 인프라 어댑터
- 조회(read-only) 서비스: **분기 로직(필터·정렬·권한·빈 결과 처리)이 있으면 단위테스트를 쓴다.**
  포트 결과를 그대로 넘기기만 하면 쿠컴버로 충분하다.
  (구 규칙은 "조회는 단위테스트 불필요"였으나 실제로 5개가 쓰여 있다 — 실제를 정본으로 개정, 2026-08-09)

## 작성 시점

- 커맨드 서비스/도메인 모델 구현 완료 시 단위테스트 같이 작성
- 쿠컴버 시나리오는 기능 구현 전 또는 구현과 동시에 작성
- 다음 기능으로 넘어가기 전에 테스트 통과 확인 필수

## E2E 테스트 (CI 자동화)

- 대상: 해피패스(`HappyPathE2eTest`) + 실DB 동시성·제약·만료.
  **"해피패스 5개"가 e2eTest의 전부가 아니다** — 무엇이 실리는지는 위 "무엇이 언제 도는가" 참조.
- 실행: `./gradlew e2eTest` (다시 돌리려면 `cleanE2eTest` 를 앞에 붙인다 — 안 그러면 up-to-date로 건너뛴다)
- 인프라: TestContainers + PostgreSQL (Cucumber과 동일)
- 인증: X-User-Id 헤더 (dev/test 환경)
- 위치: **무관** — 태그가 정본. 다만 해피패스는 `src/test/java/com/triagain/e2e/`에 둔다.

### CI에서 실제로 걸리는 게이트 (2026-08-09 확인)

`deploy.yml`의 `ci`·`e2e` 잡은 둘 다 `if: pull_request`이고, `deploy-backend`는 `if: push`인데
**`needs:`가 없다** — push 경로에서 도는 테스트는 0개다. 배포를 막는 건 브랜치 보호뿐이다.

| 브랜치 | 보호 | required check |
|---|---|---|
| `main` | 있음 | `CI (Unit + Cucumber)`, `E2E Tests` |
| `develop` | **없음** | 없음 — 빨간 CI로도 머지된다 (알고 그대로 두기로 결정) |

→ develop 머지 전 그린 확인은 **사람이 한다.** 자동으로 막히지 않는다.

## 부하 테스트 (별도 트랙)

- 도구: k6 (`load-test/`). JUnit 스위트가 아니라 `./gradlew test`/`e2eTest`와 무관하다.
- 결과 정본: `load-test/results/` (측정일 폴더별)
- ⚠️ 판정 기준은 `checks_failed`다. `http_req_failed`는 409/400 같은 **비즈니스 거절을 실패로 세어**
  서버 건강성을 왜곡한다.
- TPS 인용 시 읽기/쓰기를 분리해 적는다 (`http_reqs/s` ≠ 쓰기 TPS).

## 검증 흐름

세 층은 **파생 관계가 아니라 병렬 트랙**이다. 각자 근거 문서가 다르다.

| 층 | 무엇을 검증 | 작성 근거 | 주체 |
|---|---|---|---|
| 쿠컴버 | 유저 관점 비즈니스 플로우 (API 레벨) | `step1-biz-logic.md` | AI 초안 → 사람 리뷰 |
| 단위테스트 | 비즈니스 규칙·경계값·에러코드 | `step4-be-task.md` 테스트 표 | AI |
| E2E | 핵심 해피패스가 끝까지 도는가 | 고정 5개 | AI (CI 자동) |

E2E 층의 작성 근거는 고정 5개가 맞다. 다만 **`e2eTest` 태스크에는 이 층 말고도 e2e 태그가 붙은
실DB 동시성·제약 테스트가 같이 실린다** — 층(무엇을 검증하나)과 태스크(어디서 도나)를 혼동하지 마라.

⚠️ **쿠컴버는 단위테스트의 입력이 아니다.** "쿠컴버 → 단위테스트" 순서는 커버리지 폭
(넓음 → 좁음)이지, 시나리오에서 단위테스트를 파생시킨다는 뜻이 아니다.
