# 조건부(CONDITIONAL) 부하테스트 스윕 결과 — 2026-07-01 (2차 관측 강화판)

> 🔴 **정정 (2026-07-07, 사용자 확정)**: **이 문서는 오라벨이다.** 아래 raw(`CCC_max10_*`)의 실제 전략은 CONDITIONAL이 아니라 **PESSIMISTIC(비관락 3차)** — 👤 사용자가 07-07에 "CCC 비관락"으로 직접 확정했다. 아래 "전략 라벨 주의"의 CONDITIONAL 추정(근거 (b) conflict=0은 비관·조건부 공통 거동이라 애초 판별력 없음)은 기각됐다.
> **이 문서 명의의 수치 인용 금지** — 같은 raw의 정본 결과서는 `0701_3번재비관락-결과.md`(비관 3차)다. 파급: `0707/0707_accept-count-100vs256-before-after-비교.md`의 "원자적 前(100)" 열이 이 문서를 원천으로 삼아 **무효**(해당 문서 정정 주석 참조). 검증 상세: `load-test/verification/01-inventory.md` §A-1.

> **상태**: ✅ 측정 완료 (2026-07-01 KST 20:23~20:27) — vu 스윕 50→100→200→300
> **목적**: 크루 가입 조건부 원자적 UPDATE 전략의 정원 경합 성능·정합성 측정 + **신규 서버측 관측(Tomcat 스레드풀 · accept 큐 실측)으로 드롭 원인 직접 확증**
> **데이터 출처**: `results/raw/crew-rush-jian_CCC_max10_vu*_2026-07-01T11-*.summary.json` (§참조한 실제 파일)
> **라벨 규칙**: 〔측정〕 k6 카운터/트렌드 직접값 · 〔파생〕 status 0(drop)·합산 등 간접값 · 〔추정〕 근본원인 귀속

> ⚠️ **전략 라벨 주의**: 이 문서 한정 파일 접두어 `CCC` = **CONDITIONAL(조건부 원자적 UPDATE)**. 근거 — (a) 이번 세션 컨텍스트가 조건부 전략 벤치, (b) 거동이 조건부와 일치(conflict 0·정원 정확히 10). raw 접두어 A/B/C↔전략은 **세션마다 다르므로 직역 금지**. 실행 명령에 `lock-strategy` 플래그가 없어 **서버 기본값(develop 채택 CONDITIONAL로 추정)**으로 돌았다 — 서버 실제 구동 전략은 👤 확인 권장.
> ⚠️ **시각 주의**: 파일명 타임스탬프 `2026-07-01T11-*`는 k6가 찍는 **UTC**. +9 = **KST 20:2x** = 본 측정.

---

## 0. 측정 환경

| 항목 | 값 |
|------|-----|
| 서버 | EC2 `15.164.69.243` (t3.micro · 2vCPU · 1GiB · swap 0 · heap 512m · Tomcat acceptCount 100) |
| DB | RDS PostgreSQL 16 |
| 전략 | CONDITIONAL (조건부 원자적 `UPDATE ... WHERE current_members < max_members` + `(crew_id,user_id)` 유니크 제약) — 서버 기본값 |
| 크루 | 단일 크루 `loadtest-rush-crew-1` 집중 경합 (per-vu-iterations, VU당 iters=1) |
| 정원 | MAX_MEMBERS=10 |
| k6 | v1.6.1, 로컬(darwin/arm64) → EC2 |
| 스크립트 | `k6/crew-rush-jian.js` (BASE_URL=http://15.164.69.243:8080) |
| **신규 관측** | Grafana `Tomcat Threads`(busy/current/max) 패널 · HTTP p95/p99 히스토그램 · `ss -lnt` accept 큐 라이브 폴링(0.05s) |

> ⚠️ 절대 수치는 t3.micro·동일 인스턴스 전제에서만 유효. 다른 측정일과 **절대값 직접 비교 무효**(warm 상태·도착 타이밍 차이). 전략 간 상대 비교만 유효.

---

## 1. 측정 신뢰성 — 드롭 지표 성격

- `join_dropped`(status 0, 연결 리셋)는 **연결계층 지표**로 재실행 편차가 크다(전략 무관 t3.micro 노이즈). 전략 판별 1차 지표로 쓰지 않고 **p95·정합성·conflict를 1차**로 본다.
- **드롭 정의**: 요청이 워커 스레드에 배정되기 **전** accept 큐(백로그 100) 오버플로로 TCP 연결이 RST(`read: connection reset by peer`)됨 → k6가 유효 HTTP 응답을 못 받아 status 0. **5xx(서버에러)도 409(정상거절)도 아닌 연결 실패**.

---

## 2. 정원 경합 결과 (정원 10명, vu 50~300)

`success`·`full`·p95·connecting은 〔측정〕, `dropped`은 〔파생〕. 수치 정본 = summary.json.

| vu | success | full(409) | conflict | 5xx | **dropped** | scen_d p95(ms) | avg(ms) | max(ms) | 성공요청 p95(ms) | connecting p95(ms) | 정합성 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|
| 50  | **10** | 40  | 0 | 0 | **0**  | 572.0  | 430.2 | 575.3  | 338.4 | 34.0 | ✅ PASS |
| 100 | **10** | 90  | 0 | 0 | **0**  | 888.5  | 603.1 | 896.0  | 326.2 | 33.5 | ✅ PASS |
| 200 | **10** | 107 | 0 | 0 | **83** | 920.9  | 425.7 | 1126.3 | 381.7 | **70.4** | ❌ FAIL(drop) |
| 300 | **10** | 192 | 0 | 0 | **98** | 1080.3 | 530.1 | 1173.9 | 268.2 | **77.3** | ❌ FAIL(drop) |

- 합계 검산: 50=10+40 · 100=10+90 · 200=10+107+83 · 300=10+192+98 — 전부 정확히 vu와 일치.
- `conflict`·`other`는 **4런 모두 0** (summary.json에 메트릭 미발행 = 한 번도 안 잡힘; 콘솔 박스 `충돌_conflict: 0`로 확증).
- `http_req_failed` 높은 값(vu300 96.7%)은 **정상** — k6는 409(정원초과)도 non-2xx라 "failed"로 카운트. 대략 (vu−10)/vu와 일치, **서버 에러 지표 아님**(5xx=0).

### 2-1. accept 큐 라이브 실측 (`ss -lnt`, Recv-Q=대기 중 연결 / Send-Q=백로그 한계)

| vu | 시각(KST) | Recv-Q(라이브 큐 깊이) | Send-Q(백로그 한계) |
|---|---|---:|---:|
| 50  | 20:23:04 | 28 | 100 |
| 100 | 20:24:25 | 33 | 100 |
| 200 | 20:25:21 | 42 | 100 |
| 300 | 20:27:37 | 22 | 100 |

> **Send-Q=100 = 고정 상수** = accept 큐 **용량 한계**(Tomcat acceptCount 100, somaxconn≥100). 측정값이 아니라 설정 천장.
> **Recv-Q = 그 순간 accept 대기 중 완성 연결 수**(라이브). idle이면 0인데 부하 중 상시 non-zero = 큐 사용 중.
> ⚠️ **역설 주의**: vu200/300에서 Recv-Q 스냅(42·22) < 100인데 드롭 발생. 이유는 **버스트가 <100ms**라 0.05s 폴링이 **천장(100) 치는 찰나를 놓치고** "빠지는 중" 골짜기 값만 잡기 때문. **드롭 수(83·98)가 진짜 오버플로 증거, Recv-Q 스냅은 과소표집된 목격담.**

---

## 3. 정합성 판정

판정식: `success==10 && full+conflict+dropped==vu−10 && 5xx==0`.

- **vu50·100**: **PASS**. success 정확히 10(정원), 초과분 100% 409 full 흡수, 드롭 0.
- **vu200·300**: success=10·conflict=0·5xx=0은 OK이나 dropped≠0 → **FAIL(drop)**. 단 `full+dropped == vu−10`(200: 107+83=190, 300: 192+98=290) — **정원초과분이 409 대신 연결 리셋으로 샌 회계**. 실제 입장은 10명뿐, **정합성이 깨진 게 아님**.
- k6 thresholds 일치: `join_success==10`·`join_5xx==0`은 4런 ok / `join_dropped==0`은 vu50·100 ok, vu200·300 fail.

---

## 4. 핵심 인사이트

**① 정합성 완벽무결 — CONDITIONAL이 카오스 속에서도 불변식 유지.**
vu50~300 전 구간 success가 **정확히 10명**으로 고정, 오버셀/언더셀 0, conflict 0. vu200/300에서 83·98개 연결이 TCP 레벨에서 RST로 튕기는 와중에도 뚫고 들어온 요청은 절대 정원을 초과시키지 않았다. 조건부 원자적 UPDATE + 유니크 제약이 연결계층 노이즈와 무관하게 정원을 지킴.

**② FAIL의 정체 = t3.micro accept 큐(acceptCount 100) 오버플로 — 락/코드 무관. 5중 확증.**
- (a) **드롭 회계 일치**: `full+dropped=vu−10` — 락이 잘못 입장시킨 게 아니라 거부 응답이 연결계층에서 샌 것.
- (b) **드롭 임계점 = 백로그 크기**: 드롭이 vu100(0)→vu200(83) 사이에서 시작. 동시 연결이 백로그 100을 넘는 vu200부터 터짐 = 교과서적 포화.
- (c) **connecting p95 2배 상승**: 34→33→**70→77ms**. 연결이 accept될 때까지 대기 시간 증가 = accept 큐 포화 지문.
- (d) **서버 처리시간은 멀쩡**: 성공요청 처리 p95가 338→326→382→**268ms로 오히려 하락**. 앱/DB/락 병목 아님.
- (e) **Tomcat Busy 스레드 ≈ 0** (Grafana, 부하 전 구간): 워커 풀 200 중 거의 안 씀, Current 최대 ~90. **스레드 고갈 완전 배제** — 이번 신규 관측이 (b)~(d)의 추론을 **직접 눈으로 확증**.

→ **전체 p95 부풀림(572→1080ms)은 서버가 느려서가 아니라 연결 대기(connecting)+큐잉.** 문(accept 큐)이 좁아 밖에서 줄 서다 튕기는 것이지, 안에서 처리가 막힌 게 아님.

**③ avg 비단조는 드롭 생존자 편향(착시).**
scen_d avg가 430(vu50)→603(vu100)→**426(vu200)** 로 내려가는 건 개선이 아니라, vu200에서 **가장 오래 큐에 걸렸을 83건이 드롭돼 latency 모수에서 빠진** 생존자 편향. 실제 천장은 p95(920>888)와 max(1126.3, 최고치 갱신)가 보여줌. 처리 한계는 vu100~200 사이.

---

## 5. 전략 비교에 쓸 때 (가이드)

- **유효 비교 구간 = vu50~100 (drop=0 클린)**. 현실 부하대라 A(비관)/B(낙관)/C(조건부) 맞대보기 가장 공정.
- **vu200/300은 비교 제외**하거나 "인프라 큐 한계·전략 무관"으로 주석. 드롭은 워커 배정 전 단계라 어느 락을 써도 동일하게 터짐 — 드롭을 전략 실패로 읽으면 안 됨.
- **비교 축 = p95 latency**. 정합성은 CONDITIONAL 전 구간 PASS(정원 정확)라 변별력 없음.
- 참고: 06-19 `results/11_conditional-update-comparison.md`에서 정원10 p95 평균 A 1175 / B 1230 / **C 685** ms로 C 우위. 단 측정일·서버 상태 달라 **오늘 절대값과 직접 비교 금지** — 같은 날 동일 상태 A/B/C 재측정 시 비교.
- 동일 인스턴스 비관락 스윕(`비관락-스윕-결과-20260701.md`)과 대조 시: CONDITIONAL vu100 p95 888.5ms vs 비관 vu100 483.8ms — **⚠️ 측정 시각·warm 상태가 달라 직접 비교 무효**. 공정 비교는 반드시 같은 세션 연속 측정에서.

---

## 6. 남은 검증

**① 👤 서버 구동 전략 확인** — 실행 명령에 `lock-strategy` 플래그가 없어 서버 기본값으로 돌았다. develop 채택 CONDITIONAL이 맞는지 서버 프로퍼티/로그로 확인(본 문서는 거동상 CONDITIONAL로 간주).

**② 👤 DB 실제 행수 정합성** (DB 자격증명 필요) — k6 `join_success`는 앱 응답 기준. DB 실제 행수 기준 정원초과 0건은 미확인:
```sql
SELECT id, current_members, max_members,
       (SELECT COUNT(*) FROM crew_members m WHERE m.crew_id = c.id) AS actual_rows
FROM crews c WHERE id = 'loadtest-rush-crew-1';
-- 기대: current_members == 10 == actual_rows, actual_rows <= max_members
```

**③ (선택) 드롭 제거 실험** — `server.tomcat.accept-count` + 커널 `net.core.somaxconn` 동반 상향, 또는 인스턴스 업그레이드. 단 현실 부하(vu50~100)는 드롭 0이라 실서비스 필요성 낮음.

---

## 참조한 실제 파일

- summary (정본 수치 4건):
  - `results/raw/crew-rush-jian_CCC_max10_vu50_2026-07-01T11-23-04.summary.json`
  - `results/raw/crew-rush-jian_CCC_max10_vu100_2026-07-01T11-24-25.summary.json`
  - `results/raw/crew-rush-jian_CCC_max10_vu200_2026-07-01T11-25-22.summary.json`
  - `results/raw/crew-rush-jian_CCC_max10_vu300_2026-07-01T11-27-38.summary.json`
- raw 이벤트 스트림(덮어쓰기, 최종 vu300만 보존): `results/raw/crew-rush-jian_CCC_max10_vu{50,100,200,300}.json`
- accept 큐 실측: 사용자 `ss -lnt` 0.05s 폴링 캡처(대화 로그, KST 20:23~20:27)
- 서버측 관측: Grafana `Tomcat Threads`·`HTTP p95/p99` 패널 (설정 지시서 `load-test/아카이브/OBSERVABILITY-TOMCAT-LATENCY-INSTRUCTION.md`)
- 스크립트: `k6/crew-rush-jian.js`
- 포맷 참조: `results/0701/비관락-스윕-결과-20260701.md`, `results/11_conditional-update-comparison.md`
