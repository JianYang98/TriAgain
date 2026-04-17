# API + 스케줄러 동시 부하 테스트 (L 스케일)

- 일시: 2026-04-16 (Day 6)
- 서버: EC2 t3.micro (2 vCPU burstable, 1GB RAM) — 자세한 환경은 `00_environment.md` 참조
- 프로필: `prod,loadtest`
- 데이터: **L 스케일** — 유저 1,000 / API 크루 200 / 스케줄러 크루 200 / API 챌린지 1,000 / 스케줄러 챌린지 1,000
- 스크립트: `concurrent-test.sh http://15.164.69.243:8080 L`
  - k6 `load-peak.js` (8분: 1m→4VU → 2m→20 → 2m→40 → 2m→80 → 1m→0, writes는 1.5배)
  - 60초 ramp-up 대기 후 `fail-expired` 스케줄러 단발 트리거
- 사전 준비: 00~06 SQL L 스케일 재생성 + 토큰 1,000개 발급 + `08_reset_api_verifications.sql`

## 전체 결과

| 지표 | 결과 | 기준 | 판정 |
|------|------|------|------|
| **checks_failed (서버 에러)** | **0 / 335,210** | <1% | ✅ **PASS** |
| p95 A (읽기) | 145.95ms | <200ms | ✅ |
| p95 B (쓰기) | 199.82ms | <500ms | ✅ |
| p99 | 263.33ms | <500ms | ✅ |
| 스케줄러 Duration (fail-expired) | **874ms** | <300,000ms | ✅ PASS |
| `http_req_failed` | 5.92% | — | 참고용 (전부 duplicate 409) |
| verify_created | 1,000 (전부) | - | ✅ |
| verify_duplicate | 21,171 | - | (정상 비즈니스 거절) |
| 평균 TPS | 745/s | - | - |
| max VU | 238 | - | - |

## 경합 비교: 단독 vs 동시

### 스케줄러 (`fail-expired`, L 스케일)

| 실행 조건 | Duration | 비교 |
|----------|----------|------|
| 단독 실행 (`04_scheduler-progression.md`) | 612ms | 기준 |
| **동시 실행 (이번 측정)** | **874ms** | **+262ms / +42.8%** |

→ API 부하가 걸린 상태에서 스케줄러가 **42% 느려짐**. CPU/DB 커넥션 경합의 직접 증거.
→ 단 5분 윈도우 대비 여전히 대폭 여유.

### API (L 스케일, 전체 8분 평균)

| 지표 | 이번 측정 (동시) | 비교 대상 |
|------|------------------|-----------|
| p95 A | 145.95ms | 단독 VU-고정 S 스케일 VU 100 → 253ms (03c) |
| p95 B | 199.82ms | - |
| max iteration | **1.91s** | ← 스파이크. 스케줄러 트리거 구간 가능성 |
| checks_failed | 0% | 단독 측정도 0% (Day 1~2) |

- **서버 에러 0% 유지** — 스케줄러와 동시 실행 중에도 비즈니스 로직 안정성 유지
- `max iteration` 1.91s 스파이크는 드물었지만 존재 → 실 운영에서 "순간 응답 3초 초과" 사용자 불만 가능성 단서

## 핵심 해석

### HikariCP는 병목이 아니었다

실측 `/actuator/prometheus`:
- `hikaricp_connections_max`: 10
- `hikaricp_connections_active` (idle 시점): 0
- `hikaricp_connections_timeout_total`: **0** (누적, Day 1~6 전 구간)

→ pool 10개로 VU 238 + 스케줄러 동시 실행 흡수. connection 대기(timeout) 0건.
→ **Phase 1 규모에서 HikariCP 튜닝 불필요**. pool 늘려도 개선 여지 없음.

### 경합 소스는 CPU와 DB 레이어

- HikariCP OK, Tomcat 스레드 200 여유 → **앱 서버 CPU 또는 RDS 쿼리 경합**이 +42% slowdown 원인
- t3.micro는 2 vCPU burstable → 지속 부하 시 크레딧 소진 가능성 별도 관찰 필요
- RDS db.t4g.micro (추정, 2 vCPU) → work_mem 4MB, max_connections 79

### 실 운영 시사점 (Phase 1: 500명, 50 TPS 목표)

- L 스케일(1,000명)에서도 동시 실행 여유 → Phase 1 규모는 충분히 안전
- 실제 마감 피크 시간대에 스케줄러 돌아도 API p95 영향 < 200ms 예상
- 단, t3.micro 크레딧 소진 방지 차원에서 **장시간(>30분) 피크 모니터링** 필요

## ⚠️ 한계 / 측정 범위

### 1. 스케줄러 트리거가 ramp-up 초기 구간

`concurrent-test.sh`는 **60초 대기 후 트리거**. 해당 시점 VU는 `reads=3 / writes=6` 수준 (8분 스테이지의 12% 시점).
실제 피크 VU 238 구간에서 트리거된 게 아님.

→ 이번 +42% slowdown은 **경합의 하한선**. 진짜 피크 시점 트리거는 1.5~2초대까지 갈 가능성 (추정, 미측정).
→ Future work: `concurrent-test.sh` 대기 시간을 `4~5분` 으로 수정해 재측정.

### 2. 단일 트리거, 단발성 측정

`fail-expired` 한 번만 트리거. 실 운영은 5분 주기로 반복 실행 → 누적 부하 및 크레딧 소진 관점은 미측정.

### 3. `http_req_failed` 5.92% 해석 주의

409 duplicate(정상 비즈니스 거절)가 포함된 수치. 판정은 **`checks_failed`** 로 해야 함 (Day 2에서 확립, `03c_stress-breaking-point.md` 참조).

## Grafana 스크린샷

측정 구간: **2026-04-16 17:50:10 ~ 17:58:10 KST**
스케줄러 트리거: **17:51:10 KST**

캡처 패널 (`results/screenshots/` 폴더 아래):
- `day6_concurrent-L-hikaricp.png` — HikariCP active connections (pool 10 포화 여부)
- `day6_concurrent-L-cpu.png` — CPU 사용률 (t3.micro 2 vCPU 대비)
- `day6_concurrent-L-p95.png` — API p95 타임라인 (트리거 전후 스파이크)
- `day6_concurrent-L-gc.png` — GC frequency (max iter 1.91s 스파이크 연관)

> 스크린샷은 추후 사용자가 로컬 Grafana(http://localhost:3000)에서 위 시간대로 캡처. 로컬 Prometheus 컨테이너 유지되는 동안 언제든 추출 가능.

## 원본 로그

- `results/raw/day6_concurrent-L.log` — 전체 k6 + 스케줄러 출력
- 측정 시각 (KST):
  - k6 시작: 17:50:10
  - 스케줄러 트리거: 17:51:10 (k6 1m00s 시점)
  - 스케줄러 완료: 17:51:11 (874ms)
  - k6 종료: 17:58:10
