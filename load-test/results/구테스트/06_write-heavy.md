# Write-Heavy TPS 측정 결과 (Day 7)

> **⚠️ 정정 배너 (2026-07-29)** — 인용 전 반드시 읽을 것.
>
> 1. **"쓰기 포화 490/s"는 지속 처리량이 아니라 10~15초 구간 실측치다.** Day 7 런 길이는 VU별로
>    30/15/15/10/10초였다 (아래 "VU 스윕 결과" 표의 `시간` 열 — 원표에 이미 기록돼 있으나
>    결론 문장들이 그 조건을 떨어뜨린 채 인용돼 왔다).
> 2. **수치 자체는 유효하다.** 전 런 `verify_duplicate = 0`으로 유저풀 소진 오염이 없음을 확인했다.
>    바뀐 것은 수치가 아니라 **표현**이며, Phase 1 목표(50 TPS) 대비 **"약 10배 여유" 결론은 그대로 유지**된다.
> 3. **읽기 912 req/s는 `GET` 전용 요청 레이트다.** 전체 `http_reqs`는 915.6/s이고
>    912.66(GET) + 2.97(POST) = 915.63으로 정확히 분해된다 (`03c_vufixed-50.log`).
>    읽기 런은 2m0s 지속 측정이라 위 1번의 "구간 처리량" 문제가 **없다** — 쓰기와 조건이 다르다.
> 4. 따라서 **읽기 912 ↔ 쓰기 490 직접 비교는 단위가 다르다** (2분 지속 vs 15초 구간). 아래
>    "읽기 TPS vs 쓰기 TPS 비교" 표의 `54%` 비율은 이 조건 하에서만 읽을 것.
>
> 경위·근거: (루트) `TODO/TODO-추후-07-20-490TPS-문구정정.md`

- 일시: 2026-04-17
- 서버: EC2 t3.micro (2 vCPU burstable, 1GB RAM)
- DB: RDS PostgreSQL 16, HikariCP pool 10
- 프로필: prod,loadtest
- 데이터: XXL (유저 10,000 / 크루 2,000 / 챌린지 10,000 — 전부 TEXT)
- 스크립트: `load-write-heavy.js` (POST /verifications 단독, sleep 없음, 매 iter 고유 유저)
- 사전 조건: warm-up 완료, 매 VU 테스트 전 `08_reset_api_verifications.sql` 실행
- 관련 문서: `03c_stress-breaking-point.md` (기존 혼합 부하 읽기 TPS), 계획서 `~/.claude/plans/concurrent-splashing-gosling.md`

## 배경 — 왜 이 테스트를 했는가

기존 `load-normal.js` 혼합 부하 측정에서 "TPS 920"이라 보고된 수치의 endpoint 분해 결과:
- `GET /crews/{id}`: **912/s** (99.7%)
- `POST /verifications`: **3/s** (0.3%)
- 유효 DB INSERT: **0.41/s** (유저 50명 소진 후 409 지배)

즉 "920 TPS"는 사실상 **읽기 throughput**이었으며, **쓰기 TPS는 미측정**이었다.
본 테스트는 POST /verifications 단독 부하로 "진짜 쓰기 TPS"를 측정한다.

## 쓰기 경로 분석 (POST /verifications → CreateVerificationService)

1개 POST /verifications 요청의 DB 작업:

| 순번 | 작업 | 쿼리 타입 |
|------|------|----------|
| 1 | crew 조회 + 멤버십 검증 | SELECT |
| 2 | verification 중복 확인 | SELECT |
| 3 | challenge 조회 (비관적 락) | SELECT FOR UPDATE |
| 4 | crew 재조회 (verificationType) | SELECT (JPA L1 캐시) |
| 5 | verification 저장 | **INSERT** |
| 6 | challenge completedDays++ | **UPDATE** |

**총: SELECT 4~5건 + INSERT 1건 + UPDATE 1건 = 6~7 DB ops/req**, `@Transactional` 단일 트랜잭션.

## 판정 기준

| 구분 | 기준 |
|------|------|
| Green | p95 < 200ms AND 5xx 0% |
| Yellow (Knee) | p95 200~500ms |
| Red (Breaking) | p95 > 500ms OR 5xx > 1% |

- **TPS = `verify_created/s`** (201 응답만 카운트, 409 = 측정 무효)
- 모든 VU 레벨에서 `verify_duplicate = 0` 확인 (유저 풀 소진 안 됨)

## VU 스윕 결과

| VU | 시간 | 쓰기 TPS | p50 | p95 | 5xx | verify_dup | 판정 |
|----|------|---------|-----|-----|-----|------------|------|
| 10 | 30s | **309/s** | 29ms | 55ms | 0% | 0 | Green |
| 30 | 15s | **479/s** | 59ms | 99ms | 0% | 0 | Green |
| 50 | 15s | **491/s** | 96ms | 180ms | 0% | 0 | Green (최적점) |
| 100 | 10s | **484/s** | 193ms | 393ms | 0% | 0 | Yellow |
| 150 | 10s | **473/s** | 299ms | 594ms | 0% | 0 | Red (Breaking) |

> **측정 시간이 VU마다 다른 이유**: XXL 유저 풀(10,000)이 높은 TPS에서 빠르게 소진되므로,
> `verify_duplicate = 0`을 유지할 수 있는 최대 시간으로 조정. 모든 구간에서 dup=0 확인.

## 핵심 결론

### 쓰기 포화점 (Saturation): VU ~30-50 / TPS ~480~490/s (15초 구간 실측)
- VU 30에서 TPS 479/s, VU 50에서 491/s → 이후 VU 올려도 TPS 횡보
- **t3.micro 2 vCPU + pool 10 조합의 쓰기 처리 한계 ≈ 480~490/s** — 단, 이는 **10~15초 구간 실측치**이며
  분 단위 지속 처리량은 본 측정으로 확인되지 않았다 (런 길이 제약 — 위 스윕 표 `시간` 열 및 그 아래 주석 참조)
- VU 100 이상은 대기 시간만 증가 (p95: 180ms → 393ms → 594ms)

### 쓰기 Breaking Point: VU ~150
- p95 594ms > 500ms 기준 초과
- 서버는 5xx 0건 — OOM/crash 없이 정상적 degradation

### 읽기 TPS vs 쓰기 TPS 비교 (동일 서버)

⚠️ **두 열은 측정 조건이 다르다** — 읽기는 2m0s 지속, 쓰기는 15초 구간이다. 아래 비율은 이 점을 감안해 읽을 것.

| 지표 | 읽기 (03c) | 쓰기 (본 측정) | 비율 |
|------|-----------|-------------|------|
| 측정 창 | **2m0s (지속)** | **15s (구간)** | ⚠️ 비교 시 전제 |
| 포화 TPS | 912/s (GET 전용) | 480~490/s | 쓰기 = 읽기의 **약 54%** (단위 상이 — 참고치) |
| 포화 VU | 50 | 30-50 | 유사 |
| Breaking p95 | 639ms (VU 250) | 594ms (VU 150) | 쓰기가 더 빠르게 포화 |
| 요청당 DB ops | SELECT 4종 (readonly) | SELECT 4~5 + INSERT + UPDATE | 쓰기가 ~1.5배 무거움 |

### Phase 1 목표(50 TPS) 대비 여유

| 시나리오 | TPS | 측정 창 | 여유 배수 |
|----------|-----|--------|----------|
| 읽기 (GET /crews) | 912/s (GET 전용) | 2m0s 지속 | **18배** |
| **쓰기 (POST /verifications)** | **480~490/s** | 15s 구간 | **약 10배** |

목표가 50 TPS이므로 측정 창 차이를 감안해도 **"10배 여유" 결론은 흔들리지 않는다.**

## 이력서/블로그용 정정 수치

**정정 전 (오해 유발)**:
> "t3.micro에서 920 TPS 처리"

**정정 후 (정직)** — 2026-07-29 재정정:
> "EC2 t3.micro(2 vCPU)에서 읽기 **912 req/s**(GET 전용, 2분 지속) + **쓰기 포화 처리량 약 480~490/s**
> (15초 구간 실측, VU 30~150 평탄) 실측, VU 150까지 5xx 0%"

<details>
<summary>구 문구 (인용 금지)</summary>

> ~~"EC2 t3.micro(2 vCPU)에서 읽기 912 req/s + **쓰기 490 TPS** 처리, VU 150까지 5xx 0%"~~
> — 쓰기 수치의 측정 창(15초) 조건이 빠져 지속 처리량으로 오독될 수 있음.

</details>

## Grafana 모니터링 관찰 (스크린샷)

측정 구간: 2026-04-17 12:00~12:15 KST (warm-up + VU 10/30/50/100/150 전 구간)

| 패널 | 스크린샷 | 핵심 관찰 |
|------|---------|----------|
| 전체 대시보드 | `screenshots/day7_write-heavy-full.png` | VU 스윕 계단 형태 전경 |
| CPU | `screenshots/day7_write-heavy-cpu.png` | 최대 **85%** — VU 150에서도 CPU 여유 있음 (읽기 VU 50에서 이미 100% 찍던 것과 대비) |
| TPS (Requests/sec) | `screenshots/day7_write-heavy-tps.png` | VU 30~50 구간 TPS ~490 횡보 확인 (각 VU 10~15초 구간) |
| HikariCP | `screenshots/day7_write-heavy-hikaricp.png` | **Pending(waiting) 최대 140** — Day 6 읽기(76) 대비 **2배**. pool 10이 쓰기 부하에서 심한 병목 |
| GC | `screenshots/day7_write-heavy-gc.png` | **GC Pause 최대 4.6초** — VU 100 이상 고부하 구간에서 GC 스톱-더-월드 관측. p99 latency 악화 원인 |
| JVM Heap | `screenshots/day7_write-heavy-jvm.png` | Xmx 512m 기준 Heap 사용률 안정적 (GC 후 회수 정상) |

### 읽기 vs 쓰기 모니터링 비교

| 지표 | 읽기 (Day 6 L) | 쓰기 (Day 7) | 해석 |
|------|---------------|-------------|------|
| CPU 최대 | ~100% | 85% | 쓰기는 CPU보다 **DB I/O 바운드** — CPU 여유 있어도 TPS 못 올라감 |
| HikariCP Pending | 76 | **140** | 쓰기 트랜잭션이 길어 커넥션 점유 시간↑ → 대기열 2배 |
| GC Pause | 관측 미미 | **4.6s** | 쓰기 객체 할당률↑ → Full GC 유발. 운영 시 GC 튜닝 필요 |

### 운영 시사점

1. **HikariCP pool 10은 쓰기 부하에 부족** — pending 140은 커넥션 고갈 상태. pool 20~30 확장 시 쓰기 TPS 추가 향상 가능
2. **GC 4.6s pause는 운영 리스크** — G1GC 또는 ZGC 전환 + Heap 확장(1GB) 검토 필요
3. **CPU 85%는 여유** — 병목은 DB 커넥션 + GC이지 CPU가 아님

## 원본 로그

> 경로 정정(2026-07-29): 실제 위치는 `results/raw/`가 아니라 **`results/screenshots/raw/`** 다.

- `results/screenshots/raw/day7_write-10.{log,json}`
- `results/screenshots/raw/day7_write-30.{log,json}`
- `results/screenshots/raw/day7_write-50.{log,json}`
- `results/screenshots/raw/day7_write-100.{log,json}`
- `results/screenshots/raw/day7_write-150.{log,json}`

읽기 비교치(912 req/s)의 원본: `results/screenshots/raw/03c_vufixed-50.log`
