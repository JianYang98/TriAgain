# 작업 지시서 v3: 크루 참여 TPS — 락 전략 3종 × 경합 2케이스 (총 6런)

> v2 대비 변경: 그라운딩 결과 선반영(§2), 환경 전제 신설(§3), 측정 통제 신설(§5),
> 시드 joinable 조건 명문화(§4-1), 예외조항 삭제(§6).
> 작성: 오케스트레이션 에이전트 | 2026-07-20
> **2026-07-21 정정 2건**: §4-3 responseCallback 문법(`options` 키 → init 컨텍스트
> `http.setResponseCallback` — k6 공식문서·실측 확인), §5-1 docker 예시 프로파일
> (`loadtest` 단독 → 런북 정본 `prod,loadtest`). 상세: 실행가이드 부록 A #1·#2

---

## 0. 배경 및 목적

open model(arrival-rate) 기반으로 **선착순 크루 참여(쓰기) 경로의 TPS**를 측정하고,
**락 전략 3종을 경합 강도 2케이스에서 비교**한다.

기존 측정(2026-04, `results/구테스트/`)은 bare JVM(`-Xmx512m`) + closed model(`constant-vus`)
환경이라 운영(Docker, ergonomic heap)으로 전이되지 않는다. 본 측정이 새 정본이 된다.

### 실험 매트릭스 (3 전략 × 2 케이스 = 6런)

| | CASE A: 저경합 (K=12) | CASE B: 고경합/오픈런 (K=100) |
|---|---|---|
| PESSIMISTIC | 런 1 | 런 4 |
| OPTIMISTIC | 런 2 | 런 5 |
| CONDITIONAL | 런 3 | 런 6 |

케이스별 **목적이 다르다.** 결과 해석에 반드시 반영할 것:

- **CASE A (K=12)**: 크루당 12명 지원 → 거절 비율 낮음.
  → **성공 쓰기 TPS 한계 측정**이 주 목적.
- **CASE B (K=100)**: 크루당 100명 지원 → 거절 ~90%. 이벤트성 오픈런 재현.
  → **극한 경합에서 전략별 거동 관찰**이 주 목적
  (OPTIMISTIC 재시도 소진, PESSIMISTIC 락 대기 큐).
  성공 TPS 상한이 `rate × 10/K`로 구조적으로 제한됨(300 TPS 발사 → 성공 최대 30 TPS).
  **이 상한을 리포트에 반드시 명시**할 것 — 안 하면 "B에서 성공 TPS가 낮다"를
  전략 열위로 오독하게 된다.

---

## 1. Tier 판정

```
[TIER 판정] Tier: 2 | 근거: 프로덕션 코드·스키마 무변경, 락 전략은 기존 기동 인자로 전환,
격리된 부하테스트 트윈 환경 전용 | 판정자: orchestrator | 2026-07-20
```

**승격 조건**: 프로덕션 코드 또는 스키마 변경이 필요하다고 판단되는 순간
→ **즉시 작업 중단 + 사용자 보고** (tier-policy.md 승격 규칙). 자체 판단으로 진행 금지.

---

## 2. 그라운딩 — 이미 확인된 사실 (재조사 불필요)

아래는 오케스트레이션 에이전트가 **실제 코드로 확인 완료**한 사실이다.
그대로 전제로 삼고, 재조사하지 말 것.

| 항목 | 확인 결과 | 출처 |
|---|---|---|
| **락 전략 3종** | 전부 구현 완료. enum `PESSIMISTIC \| OPTIMISTIC \| CONDITIONAL` | `CrewLockProperties.java:18` |
| **전환 방법** | 기동 인자 `--triagain.crew.lock-strategy=<전략>` (기본값 CONDITIONAL) | `application.yml:62` |
| **낙관락 구현** | `crews.version` 컬럼 이미 존재(**V20 마이그레이션**). `@Version` 어노테이션이 아니라 `updateCurrentMembersWithVersion`로 수동 관리 | `V20__add_version_to_crews.sql`, `CrewJpaRepository.java:36` |
| **낙관락 충돌 처리** | **서버 내부 재시도** `max-retry: 3` → 소진 시 `CREW_JOIN_CONFLICT` throw | `JoinCrewService.java:40`, `application.yml:63` |
| **참여 엔드포인트** | `POST /crews/{crewId}/join` · **요청 바디 없음(null)** · `Authorization: Bearer <token>` | `scenarios.js:83`, `crew-rush-jian.js` |
| **응답 코드** | `201` 성공 / `409` 거절 3종 — **상태코드만으론 구분 불가, `error.code` 파싱 필수** | `ErrorCode.java` |
| ├ `CR002` | `CREW_FULL` — 정원 마감 | `ErrorCode.java:40` |
| ├ `CR004` | `CREW_ALREADY_JOINED` — 중복 참여 | `ErrorCode.java:42` |
| └ `CR023` | `CREW_JOIN_CONFLICT` — 낙관락 재시도 소진 | `ErrorCode.java:61` |
| **토큰 만료** | **24시간** (`loadtest` 프로파일 한정). 기본 프로파일은 30분 | `application-loadtest.yml:6` |
| **토큰 발급** | `scripts/generate-tokens.sh <서버URL> <유저수>` → `tokens.csv` (헤더 `token`) | 기존 스크립트 |
| **토큰 로드** | `lib/config.js`의 `SharedArray` + papaparse가 `tokens.csv`를 읽음 | `lib/config.js:22` |
| **가입 가능 조건** | `isJoinableStatus()` = `RECRUITING` **OR** (`ACTIVE` **AND** `allow_late_join=TRUE`) | `07_rush_reset.sql` 주석 |
| **중복 참여 제약** | 동일 크루만 불가. **다른 크루는 무제한 참여 가능** → 유저는 재사용 가능 | `biz-logic.md:35` |

### 남은 조사 항목 (이것만 확인 후 보고)

1. `POST /crews/{crewId}/join`의 **201 응답 바디 스키마** (카운터 검증에 쓸지 판단용)
2. `crews` 테이블에 본 측정용 신규 네임스페이스(`loadtest-tps-crew-*`)가
   기존 `loadtest-crew-*` / `loadtest-rush-crew-*` 와 충돌하지 않는지
3. `07_rush_crews.sql` / `07_rush_reset.sql`의 재사용 가능 범위

**혼합 조회(읽기+쓰기)는 본 지시서 범위가 아니다.** 2단계에서 별도 지시.
쓰기 단독 기준선이 나오기 전 혼합을 돌리면 해석이 불가능하다.

---

## 3. 환경 전제 (필수 — 어기면 결과가 정본 자격을 잃는다)

### 3-1. 실행 환경

- **Docker 프로드 재현 환경 전용.** 정본 규약: `CREW-RUSH-DOCKER-환경-셋업-런북.md`
- 대상: 격리된 부하테스트 트윈(별도 EC2 t3.micro + 테스트 RDS db.t4g.micro)
- **운영 환경/운영 DB 접근 절대 금지.** base URL은 환경변수 주입, 하드코딩 금지
- 이미지: `feat/load-test` 브랜치 직접 빌드 (`buildx --platform linux/amd64`, 태그 `:loadtest`)
  - ⛔ `latest` 태그 push 금지
- ⛔ **`docker run`에 `--memory` 넣지 말 것** — ergonomic heap(컨테이너 가시 RAM의 25% ≈ 256MB)이
  운영과 동일해야 하는 것이 본 재측정의 명분이다
- ⛔ **bare `java -jar` 기동 금지.** 실행가이드 문서에도 bare 명령을 쓰지 말 것

### 3-2. Spring 프로파일

**`loadtest` 프로파일 필수.** 두 가지가 여기에 묶여 있다:

| 의존 항목 | 없으면 | 출처 |
|---|---|---|
| 토큰 24시간 만료 | 30분 만료 → 6런 도중 전량 401 | `application-loadtest.yml:6` |
| `internal.api-key` | pre-GC 게이트(§5-3) 인증 실패 → 런 차단 | `application-loadtest.yml` |

---

## 4. 작업 항목

### 4-1. 시드 스크립트 (멱등, 케이스 파라미터화)

산출물: `sql/10_tps_crews.sql`, `sql/10_tps_reset.sql` (신규 파일)

#### 유저 3,000명 + 토큰

- 유저 생성은 기존 `01_users.sql` 패턴 재사용 (`loadtest-user-N`)
- 토큰: **`scripts/generate-tokens.sh <서버URL> 3000` → `tokens.csv`**
  - ⛔ `users-tokens.json` 같은 신규 포맷 만들지 말 것.
    `lib/config.js`가 `tokens.csv`를 읽으며, 포맷을 바꾸면 기존 스크립트 전부가 깨진다
    (§6의 "기존 스크립트 수정 금지"와 충돌)
  - **24시간 유효하므로 6런 전체에 1회 발급으로 충분.** 런마다 재발급 불필요
- 3,000명 근거: 버킷 내 유저 중복 방지 조건 `W×K < 유저수`를
  CASE B(W=20 × K=100 = 2,000)까지 만족

#### 크루 시드 — 🚨 joinable 조건 필수

**정원은 실제 서비스와 동일하게 `max_members = 10` 고정.**

⚠️ **기존 `02_crews.sql` 패턴을 그대로 쓰면 안 된다.** 그 스크립트는
`allow_late_join = FALSE`로 만들기 때문에 `isJoinableStatus()`를 통과하지 못하고
**6런 전량이 CR003(모집 중 아님)으로 죽는다.**

**반드시 `07_rush_crews.sql` 패턴을 따를 것:**

```sql
status          = 'ACTIVE',
allow_late_join = TRUE,      -- ← 이게 빠지면 전량 CR003
current_members = 0,
max_members     = 10,
start_date      = CURRENT_DATE - 1,
end_date        = CURRENT_DATE + 5,
visibility      = 'PUBLIC'   -- OPTIMISTIC 경로가 isPublic() 검사함
```

`07_rush_reset.sql` 주석에 이 함정이 기록되어 있다:
> `status/allow_late_join 필수: isJoinableStatus() = RECRUITING OR (ACTIVE && allow_late_join).`
> `이 둘이 빠지면 한번 꼬인 상태가 리셋으로 안 풀려 CR003(모집 중 아님) 재발.`

#### 크루 수

네임스페이스: `loadtest-tps-crew-{N}` (기존 `loadtest-crew-*` / `loadtest-rush-crew-*` 와 분리)

기본 프로파일 총 시도 ≈ **63,000** (§4-2의 rate 프로파일 기준: 50→500 램프 120s 평균 275/s
= 33,000 + 500/s 유지 60s = 30,000)

| 케이스 | 필요 크루 `ceil(63000/K)` | 시드 수 |
|---|---|---|
| CASE A (K=12) | 5,250 | **6,000** |
| CASE B (K=100) | 630 | **800** |

- 시드는 케이스를 인자로 받아 해당 크루 수 생성
- **재실행 시 초기화 후 재생성 — 런마다 반드시 리셋**
- 유저수/크루수/정원/K/W는 전부 상수 또는 인자로 조정 가능하게

#### 리셋 스크립트

`07_rush_reset.sql` 패턴 준용 (challenges → crew_members → crews UPDATE 순서).

⚠️ **CASE A 1런당 성공 가입이 약 4만 행**이다. 6런 반복 DELETE로 테이블 블로트가
누적되면 **뒤 런이 구조적으로 불리해진다(= 순서 교락)**.
→ 리셋 후 `VACUUM (ANALYZE) crew_members, crews` 실행.
→ 첫 런과 마지막 런 직전에 `pg_stat_user_tables`의 `n_dead_tup`을 기록해
   블로트가 실제로 통제됐는지 증빙으로 남길 것.

---

### 4-2. k6 스크립트 (`k6/tps-join.js`, 신규)

**기존 `k6/crew-rush-jian.js`를 참고해 새로 작성.** 요청 구성·인증·`errorCode()` 추출·
카운터 관례·handleSummary 블록은 재사용하되, executor와 분배 전략은 아래를 따를 것.
**기존 파일은 절대 수정하지 말 것.**

#### executor

```js
// ramping-arrival-rate (한계 탐색) / constant-arrival-rate (고정 rate 검증) 환경변수 선택
// 기본 프로파일: 50 → 500 TPS 램핑 2분 + 500 유지 1분 (환경변수 조정 가능)
```

- `preAllocatedVUs` / `maxVUs`는 **근거를 주석으로 남길 것**
  - ⛔ "기존 VU 250 breaking point"는 **검증되지 않은 수치다.** 인용 금지.
    실측된 스윕은 VU 10/30/50/100/150(write-heavy)과 300/700(rush)뿐이다.
  - 산정은 리틀의 법칙으로: `필요 VU ≈ 목표 rate × 예상 응답시간`.
    500 TPS × 0.5s = 250 → 여유 2배로 `maxVUs: 500` 정도. **이 계산식을 주석에 남길 것.**

#### 버킷(윈도우) 분산 전략 — 핵심 설계, 임의 변경 금지

```js
const W = parseInt(__ENV.W || '20');                    // 동시 활성 크루 수
const K = __ENV.CASE === 'B' ? 100 : 12;                // 크루당 시도 수
const iter    = exec.scenario.iterationInTest;
const bucket  = Math.floor(iter / (W * K));
const crewIdx = bucket * W + Math.floor(Math.random() * W);
const userIdx = iter % TOTAL_USERS;                     // TOTAL_USERS = 3000
```

- **설계 의도**: 활성 윈도우 W개에 트래픽을 집중시켜 선착순 마감 경합을 보존하면서,
  윈도우가 넘어가며 성공 쓰기를 지속 — 꽉 찬 크루에 트래픽이 안 들어오는 실제 패턴 재현
- **유지 조건**: `W × K < 3,000` (버킷 내 유저 중복 방지).
  A=240 ✅ / B=2,000 ✅. 상수 변경 시 이 관계가 깨지지 않는지 확인
- 이 불변식 덕분에 **`join_rejected_dup`은 구조적으로 0이어야 한다** →
  threshold `count==0`으로 박아 불변식 위반을 자동 검출 (§4-3)
- 변경이 필요하다고 판단되면 **사유 보고 후 승인** 받을 것

#### ⚠️ `random(W)` 분포의 결과 해석 (리포트에 반드시 반영)

랜덤 분배라 크루별 시도 수가 균등하지 않다. CASE A 기준 크루당 기댓값 12,
**표준편차 ≈ 3.4** → 상당수 크루가 12발 미만을 받아 **정원 미달로 종료**한다.

- **`join_success`는 `크루수 × 10`이 되지 않는다.** 실제 성공률은 83%가 아니라 70% 근처
- 성공 TPS는 반드시 **실측 `join_success` ÷ 실측 소요시간**으로 산출.
  "크루당 10 고정"을 전제한 계산 금지
- 세 전략이 **같은 분포**를 받으므로 전략 간 비교의 공정성은 유지된다 —
  분포의 절대값을 결론에 넣지 않으면 된다. 이 문장을 리포트에 명시할 것

---

### 4-3. 메트릭 및 thresholds

#### 커스텀 Counter (전략 비교표의 축)

| 카운터 | 조건 |
|---|---|
| `join_success` | `201` |
| `join_rejected_full` | `409` + `error.code == "CR002"` |
| `join_rejected_dup` | `409` + `error.code == "CR004"` |
| `join_conflict` | `409` + `error.code == "CR023"` (낙관락 재시도 소진) |
| `join_error` | `>= 500` |
| `join_dropped` | `status == 0` (연결 실패/리셋) |
| `join_other` | 위 어디에도 안 걸리는 전부 |

#### thresholds

```js
thresholds: {
  'http_req_duration': ['p(95)<500'],        // 조정 가능 상수
  'http_req_failed':   ['rate<0.01'],
  'join_rejected_dup': ['count==0'],         // 버킷 불변식(W×K<유저수) 위반 검출
  'join_error':        ['count==0'],
  'join_dropped':      ['count==0'],
}
```

#### 🚨 409를 `http_req_failed`에서 제외하는 방법

정원 마감·중복은 **비즈니스 정상 응답**이므로 실패로 세면 안 된다.
k6는 기본적으로 409를 실패로 집계하므로 **명시적 설정이 필요**하다:

```js
// [07-21 정정] 전역 등록은 init 컨텍스트(모듈 최상위, options 밖)에서 한다.
// k6 options에는 responseCallback 키가 없다(공식문서·v1.6.1 실측 확인) —
// 초판의 `options.responseCallback` 표기는 오문법으로, 그대로 쓰면 적용되지 않는다.
import http from 'k6/http';
http.setResponseCallback(http.expectedStatuses(201, 409));
// (개별 요청만 예외로 둘 땐 해당 요청 params에 responseCallback: http.expectedStatuses(...) 지정)
```

> 배경: 이 팀의 기존 인사이트 #2 — "`http_req_failed`는 4xx를 실패로 센다".
> 선언 없이 돌리면 CASE B에서 `http_req_failed`가 90%로 찍혀 threshold가 무의미해진다.

#### 보고 구분

- **"성공 쓰기 TPS"와 "전체 처리 TPS"를 반드시 분리 보고** — CASE B에서 특히 중요.
  고경합에서 409는 빠른 경로라 **전체 TPS는 오히려 올라가면서 유효 처리량은 떨어진다.**
  구분 안 하면 숫자를 거꾸로 읽는다
- `dropped_iterations` 발생 여부 필수 포함 (rate 미달성 = 한계 도달 신호)

#### RUN_TAG 가드 재설계

`crew-rush-jian.js:52`의 기존 가드는 `RUN_TAG`가 `vu{N}`으로 끝나야 통과한다.
본 스크립트는 arrival-rate라 축이 **전략·케이스·rate**이므로 **그대로 복사하면 항상 throw한다.**

```js
// [신규] RUN_TAG 형식 강제: {전략}_{케이스}_r{rate}  예) PESSIMISTIC_A_r500
// 태그 누락 시 직전 런 raw를 덮어쓰는 사고 방지 (07-09 오태그 전례)
const TAG_RE = /^(PESSIMISTIC|OPTIMISTIC|CONDITIONAL)_(A|B)_r\d+$/;
if (!__ENV.RUN_TAG || !TAG_RE.test(__ENV.RUN_TAG)) {
  throw new Error(`RUN_TAG 형식 오류(${__ENV.RUN_TAG}) — {전략}_{A|B}_r{rate} 필요, 실행 중단`);
}
```

⚠️ **단, RUN_TAG는 사람이 넣는 값이라 전략의 증빙이 될 수 없다.** 실제 라벨 확정은 §5-1을 따를 것.

---

## 5. 측정 통제 (신설 — 이게 빠지면 6런이 서로 비교 불가능해진다)

### 5-1. 🚨 전략 전환은 **서버 재기동**이다

`lock-strategy`는 `@ConfigurationProperties`라 **런타임 변경이 불가능**하다.
6런 = 최소 3회 재기동. 재기동은 JVM 힙 초기화 + JIT 워밍업 리셋을 동반하므로
**순서·성숙도 교락**이 그대로 실험에 들어온다.

필수 절차:

1. **전략 전환 = 컨테이너 재기동**
   ```
   docker run ... triagain:loadtest \
     --spring.profiles.active=prod,loadtest \
     --triagain.crew.lock-strategy=PESSIMISTIC
   ```
2. **재기동 후 워밍업 런 1회** (결과 버림) — JIT 워밍업. 본 측정 전 반드시 수행
3. **🔴 런마다 JVM 부팅시각을 기록** — `/actuator` 또는 컨테이너 시작 시각.
   이 프로젝트의 **확립된 라벨 확정 방법**이다.
   파일명 접두어는 세션마다 반전된 전례가 있어 신뢰할 수 없으며
   (A/B/C 접두어 ↔ 전략 직역 금지), 실제로 A6↔C13 비교에서 부팅시각으로 전략을 객관 확정했다.
   **부팅시각 기록이 없으면 6런 결과가 어느 전략인지 사후 증명 불가능해진다.**
4. **런 순서를 기록**하고, 가능하면 케이스 간 전략 순서를 교차
   (A: P→O→C / B: C→O→P) — 순서 교락 완화

### 5-2. 🚨 `dropped_iterations` 귀속 — nstat 병행 필수

t3.micro다. 500 TPS 램핑에서 `dropped_iterations`가 발생하면 그것이
**앱 포화**인지 **커널 accept 큐 오버플로**인지 구분할 수단이 없다.
구분하지 못하면 "전략별 한계 TPS"라는 결론 자체가 성립하지 않는다
(세 전략 모두 같은 커널 천장에 막힌 동일 수치일 수 있음).

**6런 전부 `nstat` 1초 시계열을 병행 수집할 것:**

```
TcpExtListenOverflows   # accept 큐 오버플로 = 커널 천장 지문
TcpExtListenDrops
TcpExtTCPSynRetrans
TcpPassiveOpens / TcpActiveOpens
```

⚠️ **컨테이너 netns 안에서 측정할 것.** 호스트에서 `ss`/`nstat`을 보면
도커 프록시의 백로그가 잡혀 앱의 실제 backlog가 아니다(기존 확인 사항).
→ `docker exec <container> nstat ...` 또는 `nsenter`로 컨테이너 netns 진입.
호스트 측도 함께 뜨면 좋지만, **컨테이너 측이 정본**이다.

판정 규칙: `dropped_iterations > 0` 이면서 `ListenOverflows`가 동시에 증가 →
**그 구간은 앱 포화가 아니라 TCP 수립층 천장.** 전략 비교에서 제외하고 리포트에 명시.

### 5-3. GC 통제

#### pre-GC 게이트 승계

`crew-rush-jian.js`의 `setup()` 게이트를 **그대로 이어받을 것.** 6런의 힙 초기조건을 균일화한다.

```js
// PRE_GC 기본 "on". off이면 GC 호출 없이 return (무게이트 arm)
const res = http.post(`${BASE_URL}/internal/gc`, null, {
  headers: { 'X-Internal-Api-Key': GC_API_KEY },   // application-loadtest.yml의 internal.api-key
  tags: { name: 'pre-gc' },                        // built-in http_req_* 오염 필터용
});
if (res.status !== 200) throw new Error(`pre-GC 실패 — 런 중단`);
sleep(5);  // GC 여진이 램프에 안 물리게 정착 대기
```

- `tags: { name: 'pre-gc' }`를 빠뜨리면 setup의 GC 요청이 `http_req_duration`에 섞여 p95를 오염시킨다
- 게이트 실패 시 **throw로 런 자체를 차단** (미GC 런 구조적 차단)

#### GC 종류 고정

**6런 내내 동일한 GC로 고정할 것.** 현재 부하서버 기본은 Serial GC(1GiB 에르고노믹스)이며,
별도로 G1 전환 실험이 진행 중이다. **6런 도중 GC가 바뀌면 전략 비교가 통째로 오염된다.**

- 사용한 GC를 **모든 런의 결과 파일에 기록** (`java -XX:+PrintFlagsFinal` 또는 기동 로그)
- G1 실험과 본 측정을 **같은 밤에 섞지 말 것**

---

## 6. 제약 및 금지 사항

- 🚫 **프로덕션 코드·스키마 변경 절대 금지.**
  - 락 전략 3종은 `application.yml`의 `triagain.crew.lock-strategy`로 **이미 전환 가능**하다
  - `crews.version` 컬럼은 **`V20`에 이미 존재**한다
  - 🚫 **신규 Flyway 마이그레이션 작성 금지** — 체크섬 사고 전례 있음
  - 🚫 `JoinCrewService` / `JoinCrewByInviteCodeService` / 도메인 모델 수정 금지
  - 코드 변경이 필요하다고 판단되면 **작업 중단 후 사용자 보고** (§1 승격 조건)
- 🚫 운영 환경 URL / 운영 DB 접근 금지. base URL 환경변수 주입, 하드코딩 금지
- 🚫 **기존 스크립트·결과물 수정·삭제 금지**:
  `crew-rush-jian.js`, `crew-rush.js`, `lib/config.js`, `lib/scenarios.js`,
  `sql/07_*`, lock-strategy A5/C12 비교 산출물, `results/` 이하 전부
  - ⚠️ `load-test/` 디렉토리에 **`git restore` / `git checkout` 금지** —
    미커밋 개선분이 상시 존재하며 날린 near-miss 전례가 있다
- 시드가 API로 불가능한 경우에만 테스트 환경 전용 DB 직접 삽입 허용
- 신규 산출물은 별도 파일로 작성 (§4의 파일명 준수)

---

## 7. 완료 기준 (DoD)

- [ ] §2 "남은 조사 항목" 3건 보고 완료
- [ ] Docker 환경 기동 확인 — `loadtest` 프로파일, `--memory` 미사용, 이미지 태그 확인
- [ ] 시드: CASE A(6,000 크루) / CASE B(800 크루) 각각 생성 확인
      + **joinable 조건 검증 쿼리 결과 첨부**
      (`SELECT status, allow_late_join, current_members, max_members FROM crews WHERE id LIKE 'loadtest-tps-crew-%' LIMIT 5`)
- [ ] 토큰 3,000개 `tokens.csv` 생성 확인 (`tail -n +2 tokens.csv | wc -l` == 3000)
- [ ] **저부하 스모크** (10 TPS, 30초): CASE A × 1전략, CASE B × 1전략 각 1회
      — 카운터 정상 집계, 버킷 진행 확인, `join_rejected_dup == 0` 확인, 전략 전환 동작 확인
- [ ] nstat 수집 파이프라인 동작 확인 (컨테이너 netns에서 값이 나오는지)
- [ ] pre-GC 게이트 동작 확인 (stdout에 회수량·소요시간 로그)
- [ ] 실행 가이드 문서 + 6칸 비교표 템플릿 작성 완료
- [ ] 🔴 **스모크까지 완료 후 보고 → 사용자 승인 1회 → 6런 본 측정 연속 실행**
      (**승인 전 고부하 실행 금지**)

---

## 8. 산출물 경로

| 산출물 | 경로 |
|---|---|
| k6 스크립트 | `triagain-back/load-test/k6/tps-join.js` |
| 시드 SQL | `triagain-back/load-test/sql/10_tps_crews.sql` |
| 리셋 SQL | `triagain-back/load-test/sql/10_tps_reset.sql` |
| 실행 가이드 | `triagain-back/load-test/CREW-JOIN-TPS-실행가이드.md` |
| 결과 raw | `triagain-back/load-test/results/raw/` (기존 관례) |
| 결과 정리 | `triagain-back/load-test/results/<MMDD>/` (기존 관례) |

> ⛔ `docs/loadtest/` 는 이 프로젝트의 경로 관례가 아니다. 위 경로를 따를 것.

---

## 9. 결과 보고 형식

1. **그라운딩 결과** — §2 "남은 조사 항목" 3건 (실제 코드 기준 사실만)
2. **생성/변경 파일 목록**
3. **스모크 실행 결과 요약** (카운터 값 포함, `join_rejected_dup == 0` 명시)
4. **설계 판단 및 근거** — 특히 `preAllocatedVUs`/`maxVUs` 산정 계산식
5. (본 측정 후) **6칸 비교표**

   | 전략 | 케이스 | 목표 rate | 달성 rate | **성공 쓰기 TPS** | 전체 처리 TPS | p95 | p99 | `join_conflict` | `join_rejected_full` | `dropped_iterations` | `ListenOverflows` | JVM 부팅시각 | 한계 TPS 판정 근거 |
   |---|---|---|---|---|---|---|---|---|---|---|---|---|---|

6. **케이스별 해석**
   - CASE A: 성공 TPS 한계
   - CASE B: 극한 경합 거동 + **성공 TPS 구조적 상한(`rate × 10/K`) 명시**
   - `random(W)` 분포로 인해 크루당 성공이 10 고정이 아님을 명시
7. **커널 천장 판정** — `dropped_iterations`가 발생한 구간에 대해
   `ListenOverflows` 동시 증가 여부로 앱 포화 / TCP 수립층 천장 귀속
8. **미해결/보류 사항**

---

## 부록: v2 대비 변경 요약

| # | 변경 | 사유 |
|---|---|---|
| 1 | §2 그라운딩 6개 → **확인 완료 표 + 남은 3건** | 락 전략·낙관락·토큰만료·에러코드가 이미 코드에 존재 — 재조사는 시간 낭비 |
| 2 | §1 "락 전략 전환 설정이 없을 경우 예외" **삭제** | 설정이 존재하므로 발동 불가. 조항을 남기면 도메인 코드 변경(Tier 3 승격) 여지가 생김 |
| 3 | "@Version 필드 추가" 문구 **삭제 + 마이그레이션 금지 명문화** | `V20`에 version 컬럼 이미 존재. 신규 마이그레이션은 Flyway 체크섬 사고 |
| 4 | §3 **환경 전제 신설** (Docker/프로파일) | 재측정의 명분이 Docker 런타임 패리티인데 v2에 런타임 지정이 없었음 |
| 5 | §4-1 **joinable 조건 명문화** | `02_crews.sql`은 `allow_late_join=FALSE` → 그대로 쓰면 6런 전량 CR003 |
| 6 | §5-1 **재기동 절차 + JVM 부팅시각 기록 신설** | 전략 전환이 재기동이라 순서·성숙 교락 발생. 라벨 확정은 부팅시각이 정본 |
| 7 | §5-2 **nstat 병행 신설** | t3.micro. `dropped_iterations`의 앱/커널 귀속 수단이 v2에 없었음 |
| 8 | §5-3 **GC 통제 신설** | pre-GC 게이트 승계 + 6런 GC 고정 (G1 실험과 분리) |
| 9 | §4-2 `random(W)` **분포 해석 경고 추가** | "성공 10 + 초과 2"는 기댓값일 뿐. 실제 성공률 ~70% |
| 10 | §4-3 **409 제외 방법 명시** (`responseCallback`) | v2는 "제외하라"만 있고 방법이 없었음 |
| 11 | §4-3 **RUN_TAG 가드 재설계** | 기존 가드는 `vu{N}` 전제 → arrival-rate에선 항상 throw |
| 12 | 토큰 산출물 `users-tokens.json` → **`tokens.csv` 유지** | `lib/config.js`가 CSV를 읽음. JSON은 "기존 스크립트 수정 금지"와 자기모순 |
| 13 | "VU 250 breaking point" **삭제 + 리틀의 법칙 산정으로 대체** | 250은 검증되지 않은 수치 |
| 14 | §2-7 혼합 조회 조사 **삭제(2단계로 격하)** | 6런이 전부 쓰기 단독이라 쓸 곳이 없음. 기준선 없는 혼합은 해석 불가 |
| 15 | 경로 `docs/loadtest/` → **`load-test/`** | 기존 문서 관례 |
| 16 | §4-1 **VACUUM + 블로트 증빙 추가** | 1런 4만 행 × 6런 누적 블로트 = 순서 교락 |
| 17 | §1 **Tier 2 선판정 + 승격 조건 명시** | v2는 판정을 에이전트에 위임 — 예외조항과 결합되면 Tier 3 작업이 무승인 진행될 수 있었음 |
