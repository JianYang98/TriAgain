# Breaking Point 탐색 결과 (Stress Test) — 깨끗한 측정

> ⚠️ **수치 정정 고지 (2026-04-17 추가)**
>
> **쓰기 TPS 별도 측정 완료** → `06_write-heavy.md` 참조.
> 결과: 쓰기 포화점 VU 30-50 / **약 480~490/s** / p95 180ms, 5xx 0%.
> ※ **재정정(2026-07-29)**: 이 쓰기 수치는 **10~15초 구간 실측치**이지 지속 처리량이 아니다.
> 반면 본 문서의 읽기 측정은 **2m0s 지속**이라 조건이 다르다 — 둘을 직접 비교하지 말 것.
> 상세는 `06_write-heavy.md` 상단 정정 배너.
>
> 본 문서에 기재된 **"TPS 920"은 k6 `http_reqs/s` 네트워크 레이어 수치**이며,
> 실제 endpoint 분해 시 **99.7%가 `GET /crews/{id}` (읽기 트랜잭션, 4 SELECT)**,
> **`POST /verifications`는 0.3% 수준**이다.
> 유효한 DB INSERT(`verify_created`)는 **전 VU 구간에서 공통 50건/2분 = 0.41/s**
> (50명 유저 소진 후 유니크 제약 409로 INSERT 롤백).
>
> 즉 k6의 "TPS 920" = **"읽기 912/s + 쓰기 시도 3/s(유효 INSERT 0.4/s) 혼합"**이며,
> "범용 처리량 920 TPS"로 해석하면 오해.
> 상세 분해는 본 문서 말미 `부록: Endpoint별 TPS 분해` 참조.
>
> 서버 사이드 대조(Grafana `TPS (Requests/sec)` Day 6 L 스크린샷)에서도 925~950/s 관측 →
> k6 수치 자체는 부풀려진 값이 아니다. 단지 **읽기 편향**이라는 점만 명시 필요.

- 일시: 2026-04-16 (Day 2)
- 서버: EC2 t3.micro (2 vCPU burstable, 1GB RAM) — 과거 문서에 t2.micro로 기록된 건 오류, IMDS로 t3.micro 확인
- 프로필: prod,loadtest
- 데이터: S단계 (유저 50, 크루 10 — 전부 TEXT)
- 스크립트: load-normal.js (VU-고정 × 2분, A:B = 90:10)
- 사전 조건: warm-up 완료, 매 VU 테스트 전 `08_reset_api_verifications.sql` 실행
- 관련 문서: `01_vuser-fixed-normal-clean.md`, `02_saturation-clean.md`, `03a_rerun-with-reset.md`

## 판정 기준 (3단계)

| 구분 | 기준 |
|------|------|
| 🟢 Green (여유) | p95 A < 200ms AND 서버 에러율 < 0.1% |
| 🟡 Yellow (Knee / 포화) | p95 A 200~500ms OR TPS 포화 후 횡보 |
| 🔴 Red (Breaking) | p95 A > 500ms OR TPS 명확히 하락 OR 서버 에러율 > 1% |

**서버 에러율** = `checks_failed` / `checks_total` (201/409 외 응답만)

## VU 스윕 결과 (10 → 300)

| VU | TPS | p95 A | p95 B | verify_created | 서버 에러율 | 판정 |
|----|-----|-------|-------|----------------|-------------|------|
| 10 | 662/s | 18ms | 23ms | 50 | 0% | 🟢 Green |
| 30 | 920/s | 54ms | 74ms | 50 | 0% | 🟢 Green |
| 50 | 919/s | 99ms | 113ms | 50 | 0% | 🟢 **Green (최적점)** |
| 100 | 874/s | 253ms | 274ms | 50 | 0% | 🟡 Yellow |
| 150 | 874/s | 392ms | 407ms | 50 | 0% | 🟡 Yellow |
| 200 | 887/s | 473ms | 481ms | 50 | 0% | 🟡 Yellow |
| 250 | 873/s | 639ms | 643ms | 50 | 0% | 🔴 **Red (Breaking Point)** |
| 300 | 867/s | 729ms | 747ms | 50 | 0% | 🔴 Red |

## 핵심 결론

### 포화점 (Saturation Point): VU ~50 / TPS ~920/s
- VU 30에서 이미 TPS 920/s 도달
- VU 50~300 내내 TPS는 870~920 사이 횡보 — **1 vCPU 단일 스레드 프로세싱 한계**
- 이 이상 VU를 올려도 처리량은 증가하지 않고, 대기 시간만 늘어남

### Breaking Point: VU ~250
- VU 200: p95 A 473ms — 기준(500ms) 직전
- VU 250: p95 A 639ms — 기준 초과, 체감 응답성 저하
- VU 300: p95 A 729ms — 유저 이탈 시작 수준

### 서버는 300 VU에서도 다운되지 않음
- **모든 VU 레벨에서 `checks_failed = 0`** — 서버가 5xx를 반환한 적이 단 1건도 없음
- `http_req_failed`는 전부 duplicate 409 (50 유저 소진 후 자연 발생)
- OOM / Crash 없이 degradation만 발생하는 **정상적 포화 거동**

### 운영 권장 수치 (Phase 1 기준)
- **최적 운영**: VU 50 이하 유지 (여유도 확보)
- **알람 기준**: VU 100 초과 시 경보 (p95 200ms 초과 시작)
- **긴급 증설 기준**: VU 200 초과 (Breaking Point 진입 직전)
- **Phase 1 목표 (50 TPS)** 대비 여유: **~18배** (920 ÷ 50)

## 어제(오염) vs 오늘(깨끗) 비교

### 공통 VU 레벨 비교

| VU | 어제 TPS | 오늘 TPS | 차이 | 어제 p95 A | 오늘 p95 A | 차이 | 어제 http_req_failed | 오늘 서버 에러율 |
|----|---------|---------|------|-----------|-----------|------|---------------------|-----------------|
| 50  | 872/s | 919/s | **+5%** | 103ms | 99ms  | ≈    | 0.34% | **0%** |
| 100 | 898/s | 874/s | -3%     | 237ms | 253ms | +7%  | 0.60% | **0%** |
| 150 | 879/s | 874/s | ≈       | 353ms | 392ms | +11% | 0.90% | **0%** |
| 300 | 780/s | 867/s | **+11%**| 449ms | 729ms | +62% | 1.49% | **0%** |

### 관찰 및 해석

1. **TPS 차이 — 혼합 방향**
   - VU 50에서 +5%, VU 300에서 +11%: PHOTO 크루 400 거절이 사라지면서 실질 쓰루풋 상승
   - VU 100, 150에서 거의 동일: 이미 단일 vCPU 한계 근처라 오차 범위

2. **p95 A 차이 — 오늘이 다소 높게 보일 수 있음**
   - 어제는 PHOTO 크루에 걸린 write 요청이 가드(`CreateVerificationService:76`)에서 즉시 400으로 빠르게 거절됨 → **에러 응답이 빨라서 p95를 낮춰 보이게 한 착시**
   - 오늘은 모든 write가 정상 경로로 처리(201 또는 409) → DB 경합·서비스 레이어 거치는 실제 응답 시간 반영
   - 특히 VU 300에서 차이 큼(449ms → 729ms): 어제는 포화 상태에서 20% 요청이 early-exit → 대기열이 덜 쌓임

3. **에러율 지표의 진실**
   - 어제 `http_req_failed` 0.34~1.49%는 **PHOTO 400 + duplicate 409 혼합** — 서버 건강성 왜곡
   - 오늘 `checks_failed` 0% — 서버가 정상 처리 경로에서 5xx를 낸 적이 없음을 명확히 확인
   - 어제도 서버 에러 자체는 0%였을 가능성 크지만, 당시 메트릭으로는 구분 불가

### 결론 — 오염 수치는 쓰루풋을 과소평가, 지연을 과대평가, 에러율을 혼동

오늘 깨끗한 측정으로 확정:
- **Phase 1 목표(50 TPS) 대비 920/s 달성 — 약 18배 여유**
- **서버 안정성: 10~300 VU 구간 전체 5xx 에러 0**
- **Breaking Point: VU 250 (p95 > 500ms)**

## Grafana 모니터링 관찰 포인트

(EC2 t2.micro, Prometheus scrape 5s interval)

- **CPU**: VU 50에서 이미 단일 vCPU 포화 근처 — VU 100 이상은 명확한 100% 고정
- **JVM Heap**: Xmx512m 기준, 일관되게 여유 있음 (부하테스트 중 GC 발생 빈도 정도만 모니터링)
- **HikariCP active connections**: pool 기본값(10)의 포화 여부 — VU 증가에 따라 acquire 대기 발생하는지 확인
- **GC**: 빈도/시간이 응답 latency에 영향 주는지 — 특히 VU 200 이상 고부하 구간

(구체 수치는 Grafana 대시보드 스냅샷 별첨 필요 — Day 3 블로그 정리 시 캡처)

## 원본 로그

- `results/raw/03c_vufixed-100.{log,json}`
- `results/raw/03c_vufixed-150.{log,json}`
- `results/raw/03c_vufixed-200.{log,json}`
- `results/raw/03c_vufixed-250.{log,json}`
- `results/raw/03c_vufixed-300.{log,json}`

## 다음 단계

- 스케줄러 테스트 (S→M→L→XL): `FailExpiredChallengesScheduler` 외 6개 스케줄러 단계별 부하
- 스케줄러 + API 동시 부하: 가장 현실적 시나리오

---

## 부록: Endpoint별 TPS 분해 (2026-04-17 추가)

### 배경
"TPS 920"이 실제로 어떤 요청의 TPS인지 검증. `load-normal.js` + `lib/scenarios.js:16-62`
기준으로 각 iteration이 호출하는 HTTP를 재구성.

- `readScenario`: `GET /crews/{crewId}` × 1 (check `A`)
- `writeScenario`: `GET /crews/{crewId}` + sleep(1) + `POST /verifications` × 1 (check `B`)
- `/actuator/prometheus`는 Prometheus 컨테이너가 5s 간격으로 별도 scrape — k6 `http_reqs`에는 **미포함**

### 분해 공식
- write iters = `verify_created + verify_duplicate` (각 write iter는 정확히 1쌍을 남김)
- read iters = `iterations - write iters`
- http_reqs = read_iters × 1 + write_iters × 2 ← 모든 VU 로그에서 숫자 일치 확인 ✓

### VU별 실측 분해 (측정 시간 ≈ 120.9s)

| VU | GET /crews TPS | POST /verifications TPS | **실 DB INSERT TPS** (201) | 409 rollback TPS | checks_failed |
|----|----------------|-------------------------|---------------------------|-------------------|---------------|
| 10 | 661.6/s | 0.99/s | 0.417/s (50건) | 0.57/s | 0 |
| 30 | 914.8/s | 1.98/s | 0.414/s (50건) | 1.57/s | 0 |
| 50 | 912.6/s | 2.97/s | 0.414/s (50건) | 2.56/s | 0 |
| 100 | 868.6/s | 5.45/s | 0.413/s (50건) | 5.04/s | 0 |
| 150 | 867.7/s | 7.93/s | 0.413/s (50건) | 7.51/s | 0 |
| 200 | 876.5/s | 10.4/s | 0.413/s (50건) | 9.98/s | 0 |
| 250 | 859.9/s | 12.9/s | 0.413/s (50건) | 12.45/s | 0 |
| 300 | 851.5/s | 15.3/s | 0.412/s (50건) | 14.90/s | 0 |

### 해석

1. **읽기 편향**: 전 VU 구간에서 GET이 99% 이상. `POST /verifications` 비중은 0.3%(VU50) → 1.8%(VU300)까지 증가하지만 여전히 소수.
2. **유효 INSERT는 고정값**: 유저 풀 50명이 각자 1회 성공하면 그 다음부터는 전부 DB 유니크 제약 409. "쓰기 쓰루풋"을 측정한 게 아님.
3. **GET도 단순 조회가 아님**: `GetCrewService.java:33-82` 기준 요청 1건당 SELECT 4종
   (crew+members / in-progress challenges / success count / user profiles) 실행.
   단 `@Transactional(readOnly = true)` 읽기 트랜잭션 + S단계(크루 10개)라 PG shared_buffers 히트율 높음.
4. **서버 측 대조**:
   - Grafana `TPS (Requests/sec)` Day 6 L 동시부하: **925~950/s 관측** (스크린샷 `screenshots/day6_concurrent-L-tps.png`)
   - k6 http_reqs와 큰 괴리 없음 → k6 수치 자체는 신뢰 가능
   - Prometheus TSDB는 `stop-monitoring.sh`로 파기되어 VU-스윕 시점의 `http_server_requests_seconds_count`/`hikaricp_connections_acquire_seconds_count` 누적값 역산은 불가

### 결론 — 정직한 요약 문구

- ❌ 부정확: "t3.micro에서 920 TPS 달성"
- ✅ 정확: "S단계(유저 50/크루 10) · A:B=9:1 혼합 부하에서
  `GET /crews/{id}` **912/s** + `POST /verifications` **3/s(유효 INSERT 0.4/s)** 처리,
  서버 측 5xx 0건" — **2m0s 지속 측정** (912.664 GET/s + 2.970 POST/s = 915.634 `http_reqs`/s)
- ✅ 보조: "쓰기 내구 한계는 본 측정의 설계 범위 밖 (유저 풀 소진으로 409 지배) —
  별도 write-heavy 시나리오 측정 필요"
