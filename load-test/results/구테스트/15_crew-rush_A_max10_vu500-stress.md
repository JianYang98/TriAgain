# 15. 크루 가입 러시 — 전략 A(비관락/PESSIMISTIC) · 정원 10 · **VU 500** 단일 스트레스 측정

> **상태**: ✅ 단일 런 실측 (2026-06-21 01:16 KST). 기존 21측정 매트릭스(정원10 = VU 50/100/200/300)를 **VU 500까지 연장**한 스트레스 포인트.
> **이 문서의 성격**: 단일 런 보고서. `14_abc-final-comparison`(A/B/C 채택 결론)을 **대체하지 않음** — cap10 A-시리즈 곡선에 데이터 1점 추가.
> **데이터 출처**: `results/raw/k6-report_A_max10_vu500_2026-06-20T16-16-52.summary.json` + `results/raw/crew-rush_A_max10_vu500.json`
> **라벨 규칙**: 〔측정〕 k6 트렌드/카운터 직접값 · 〔파생〕 합산·간접·미출력=미발생 · 〔추정〕 근본원인 귀속

---

## ⚠️ 전략 라벨 주의 (먼저 읽을 것)

| 컨벤션 | A | B | C |
|--------|---|---|---|
| **raw 파일/런북** (이 파일을 생성한 규칙) | **PESSIMISTIC (비관락)** | OPTIMISTIC | CONDITIONAL |
| Notion 문서 `14_abc-…` (발표용 재라벨) | CONDITIONAL | OPTIMISTIC | PESSIMISTIC |

- 이 런의 태그 `A_max10_vu500` 은 **raw/런북 컨벤션 = 비관락(SELECT FOR UPDATE)** 이다. (= 문서11-A = 문서14-C)
- 문서14는 발표 편의상 A↔C를 뒤집었다(`14_…OLD-LABELS.bak`가 증거). **두 문서의 "전략 A"는 서로 다른 전략**이니 혼동 금지.
- ⚠️ **k6 콘솔/summary로는 서버가 실제 어떤 `--triagain.crew.lock-strategy`로 떴는지 확인 불가.** 데이터 지문(conflict=0·재시도 0)은 **OPTIMISTIC을 배제**하지만 PESSIMISTIC vs CONDITIONAL은 구분 못 한다. **확정하려면 EC2 기동 로그(런북 Phase 3) 교차확인 필요.** 본 문서는 런북 컨벤션대로 **비관락**으로 기술한다.

---

## 0. 측정 설정

| 항목 | 값 |
|------|-----|
| 전략 | A = 비관락 (SELECT FOR UPDATE) — *서버 로그 교차확인 권장* |
| 정원(MAX_MEMBERS) | 10 |
| 부하(TARGET_VUS) | **500** VU · `per-vu-iterations` · iters=1 (각 VU 1회 가입 시도) |
| 경합 대상 | 단일 크루 `loadtest-rush-crew-1` 집중 (500-way 단일 행 경합) |
| 서버 | EC2 `15.164.69.243` (t3.micro 전제 · HikariCP pool=10) |
| k6 | v1.6.1, 로컬(darwin/arm64) → EC2, 평문 HTTP(TLS=0) |
| 테스트 소요 | `testRunDurationMs` = **2058 ms** (버스트성, vus 피크 317) |

---

## 1. 한 줄 결론

**정합성은 완벽(succ 10 / full 490 / 초과 0 / conflict 0 / drop 0 / 5xx 0), p95 임계(1s)는 초과(1774 ms).**
지연의 정체는 서버 장애가 아니라 **락 패배자 490명이 단일 행 직렬화 큐에서 대기한 것** — 가입 성공자 10명의 p95는 **215 ms**로 임계의 1/5다.

---

## 2. 정합성 (헤드라인) 〔측정〕

| 지표 | 값 | 임계 | 판정 |
|------|---:|------|:---:|
| `checks` ("D: join or full") | 500/500 (rate 1.0) | — | ✅ 100% |
| `join_success` | **10** | `count==10` | ✅ |
| `join_full` (CR002 CREW_FULL) | **490** | `count==490` | ✅ |
| `join_conflict` (CR023) | 0〔파생: 미출력〕 | — | ✅ |
| `join_dup` (CR004) | 0〔파생: 미출력〕 | — | ✅ 토큰 오염 없음 |
| `join_5xx` | 0〔파생: 미출력〕 | — | ✅ 앱 에러 없음 |
| `join_dropped` (status 0) | 0〔파생: 미출력〕 | — | ✅ 이번 런 연결 손실 없음 |

- `full 490 = VU 500 − 정원 10`, `succ 10 = 정원` → **응답 레벨 정원초과 0건.** 500-way 단일 행 경합에서도 락이 정확히 직렬화.
- ⚠️ **DB 실제 행수 기준 초과 0건은 미확인** — `current_members==10 AND COUNT(crew_members)==10` SQL 확인은 👤 사용자 몫(문서11 §7).

---

## 3. 지연 분석 — "느린 건 진 사람들, 이긴 사람은 빠르다" 〔측정〕

| 지표 | avg | med | p90 | p95 | max | 임계 |
|------|---:|---:|---:|---:|---:|---:|
| `http_req_duration` (전체 500) | 1006 | 977 | 1701 | **1774** | 1840 | p95<1000 → ❌ |
| ┗ `{expected_response:true}` (성공 10) | 180 | 182 | 212 | **215** | 219 | — |
| `scenario_d_duration` | 1006 | 977 | 1701 | **1774** | 1840 | p95<1000 → ❌ |
| `http_req_waiting` (서버 처리 대기) | 1005 | 977 | 1694 | 1768 | 1840 | — |
| `iteration_duration` | 1118 | 1117 | 1831 | 1892 | 2040 | — |

**해석**
- `waiting ≈ duration` → 지연의 거의 전부가 **서버측 대기**(네트워크 송수신 아님: sending avg 0.02 ms, receiving avg 0.97 ms).
- **성공 응답(2xx) p95 = 215 ms** 〔측정〕. 임계 1 s를 크게 밑돈다. 느린 꼬리는 **HTTP 409(full)로 끝난 490건**이 만든다.
- 〔추정〕 비관락 구조상 full 응답도 "락 획득 → 정원 확인 → 거절"을 거치므로, 500명이 단일 행 `SELECT FOR UPDATE` 큐에 직렬로 줄 선다. 큐 배수 시간이 곧 p95(≈1.77 s)·max(≈1.84 s). avg≈1 s는 큐의 절반 지점.
- 따라서 **p95 임계 실패는 "서버가 아픈 것"이 아니라 "단일 행 직렬화의 구조적 한계"** — 정원 경합 API의 본질적 특성이다.

---

## 4. ⚠️ `http_req_failed 98%` 는 실패가 아니다 (해석 함정)

- `http_req_failed`: rate **0.98** (passes 490 / fails 10). k6 기본값은 **non-2xx를 "failed"로 집계**한다.
- 여기서 "failed" 490건은 전부 **HTTP 409 CREW_FULL = 비즈니스적으로 올바른 정원초과 거절**이다. 장애가 아니다.
- 실제 실패(5xx·연결손실)는 **0건**(`join_5xx`/`join_dropped` 미출력). → **이 98%를 에러율로 읽으면 오독.** 정합성 정본은 `checks 100%` + `join_*` 카운터.

---

## 5. 연결계층 — accept-queue 압력(꼬리만) 〔측정〕

| 지표 | avg | med | p95 | max |
|------|---:|---:|---:|---:|
| `http_req_blocked` | 111.8 | 85.3 | 117.9 | **2015** |
| `http_req_connecting` | 111.7 | 85.3 | 117.9 | **2015** |
| `http_req_tls_handshaking` | 0 | 0 | 0 | 0 |

- `blocked ≈ connecting`(TLS=0) → blocked 시간은 전부 **TCP 연결 수립 대기**다(DNS/TLS 아님).
- p95는 118 ms로 얌전하나 **max 2.01 s**의 단발 꼬리 존재 → 500개 동시 SYN 중 일부가 **accept 큐에 적체**된 지문〔추정: t3.micro accept-queue/풀 상류 압력〕. 이번 런은 그 압력이 **status 0(drop)으로까지 번지진 않음**(drop 0).
- 문서11 §6 결론과 일치: 극단 VU의 연결 한계는 **인프라 천장(t3.micro + HikariCP pool=10)**이지 락 전략 특성이 아니다.

---

## 6. cap10 A-시리즈(비관락) 곡선에 편입

문서11(=비관락) / 문서14-C(=비관락) 의 정원10 A-계열에 이번 VU 500을 추가:

| VU | succ | full | conflict | drop | p95 전체(ms) | p95 성공(ms) |
|---:|:---:|:---:|:---:|:---:|---:|---:|
| 50  | 10 | 40  | 0 | 0 | 924  | 700 |
| 100 | 10 | 90  | 0 | 0 | 863  | 397 |
| 200 | 10 | 190 | 0 | 0 | 1367 | 361 |
| 300 | 10 | 290 | 0 | 0 | 1545 | 354 |
| **500** | **10** | **490** | **0** | **0** | **1774** | **215** |

- **정합성**: 전 구간 `succ==10`, conflict 0 유지 — VU 500에서도 깨지지 않음.
- **p95 전체**: VU에 따라 단조 증가(924→1774). 경합 인원(=full)이 곧 직렬화 큐 길이라 예상대로.
- **p95 성공**은 오히려 가장 낮음(215 ms) — 성공자는 경합 초반에 락을 먼저 잡고 빠르게 빠지는 선착 그룹〔추정〕이라 부하가 커져도 빠른 경로를 탄다.

---

## 7. 판정 & 남은 확인

**판정**: VU 500 단일 행 러시에서 **비관락의 정합성은 견고**(초과/충돌/에러 0). 임계 미달은 p95 전체뿐이며, 이는 **정원 경합 API의 구조적 직렬화** 때문 — 가입 성공자 체감(215 ms)은 양호. **p95(전체) 1 s 임계 자체가 "거절 응답까지 1 s 내"를 요구하는지** 재검토 여지(거절은 느려도 무방하다면 성공 p95를 SLO로 봐야 함).

**남은 확인**
1. 👤 **서버 lock-strategy 확정** — EC2 기동 로그에서 이 런이 PESSIMISTIC였는지 교차확인(§라벨 주의). 데이터는 OPTIMISTIC만 배제, PESS/COND 구분 불가.
2. 👤 **DB 정원초과 0건** — `current_members==10 AND COUNT(crew_members)==10` (문서11 §7 SQL).
3. (선택) drop을 줄이려면 락 전략이 아니라 **HikariCP 풀 크기·accept-count·인스턴스 스케일** (문서11 §6).
4. ⚠️ **prod 배포 금지** — feat/load-test 한정.

---

## 참조한 실제 파일

- `results/raw/k6-report_A_max10_vu500_2026-06-20T16-16-52.summary.json` (정본 수치)
- `results/raw/crew-rush_A_max10_vu500.json` (raw 이벤트 스트림, 2.2 MB)
- `k6/crew-rush.js` (시나리오 D: thresholds `join_success==10`·`join_full==490`·`p95<1000`)
- `CREW-RUSH-ABC-RUNBOOK.md` (A=PESSIMISTIC 태그 컨벤션, Phase 3·5)
- `results/11_conditional-update-comparison.md` (cap10 A-계열 비교 기준)
- `results/14_abc-comparison-notion-S3S4S7.md` (재라벨 컨벤션 — 라벨 충돌 출처)
</content>
</invoke>
