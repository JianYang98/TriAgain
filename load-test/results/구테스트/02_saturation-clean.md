# 포화점 탐색 (Saturation Test) 결과 — 깨끗한 재측정

- 일시: 2026-04-16 (Day 2)
- 서버: EC2 t2.micro (1 vCPU, 1GB RAM)
- 프로필: prod,loadtest
- 데이터: S단계 (유저 50, 크루 10 — 전부 TEXT)
- 스크립트: saturation.js (VU 10→300, 10분, A:B = 90:10)
- 사전 조건: warm-up 완료, 시작 전 `08_reset_api_verifications.sql` 실행
- 원본: `results/02_saturation.md` (오염된 데이터)
- 재측정 배경: `03a_rerun-with-reset.md`

## 전체 결과

| 지표 | 결과 | 기준 | 판정 |
|------|------|------|------|
| p95 (A 읽기) | 443ms | <200ms | FAIL (ramping 구간 포함) |
| p95 (B 쓰기) | 564ms | <500ms | FAIL (ramping 구간 포함) |
| **서버 에러율 (checks_failed)** | **0%** | <1% | ✅ **PASS** |
| `http_req_failed` (전체) | 1.39% | — | 참고용 (전부 duplicate) |
| 평균 TPS | **829/s** | - | - |
| max VU | 321 | - | - |

※ p95 FAIL은 ramping 구간(100~300 VU 포함) 전체에 걸쳐 측정된 값 — 포화점·Breaking Point는 VU-고정 테스트(`03c_stress-breaking-point.md`)에서 더 정확히 분리 확인 가능.

## 상세 수치

| 지표 | avg | med | p90 | p95 | max |
|------|-----|-----|-----|-----|-----|
| A (읽기) | 129ms | 70ms | 324ms | 443ms | 3.13s |
| B (쓰기) | 200ms | 144ms | 441ms | 564ms | 2.52s |

- verify_created: 50 (모든 유저-크루 조합 최초 인증 성공)
- verify_duplicate: 6,938 (유저 소진 후 자연 발생)
- checks_failed: **0** (서버 에러 없음)

## 포화점 종합 분석 (01-clean + 03c 통합)

| VU | TPS | p95 (A) | 상태 |
|----|-----|---------|------|
| 10 | 662/s | 18ms | 여유 |
| 30 | 920/s | 54ms | 여유 |
| **50** | **919/s** | **99ms** | **최적점 (p95 기준 내, TPS 포화 시작)** |
| 100 | 874/s | 253ms | p95 초과 시작 |
| 150 | 874/s | 392ms | p95 초과 |
| 200 | 887/s | 473ms | p95 초과 (B는 기준 내) |
| 250 | 873/s | 639ms | Breaking 영역 |
| 300 | 867/s | 729ms | Breaking |

- **포화점 (Saturation Point)**: VU 30~50 / TPS **~920/s**
- **Breaking Point**: VU 200~250 (p95 A > 500ms 돌입)
- 300 VU에서도 서버 다운 없이 동작, **checks_failed 0 유지** (서버 에러 0%)

## 어제(오염) vs 오늘(깨끗) 비교

| 지표 | 어제 | 오늘 | 차이 |
|------|------|------|------|
| 평균 TPS | 780/s | **829/s** | +6% |
| p95 A | 449ms | 443ms | 거의 동일 |
| p95 B | 564ms | 564ms | 동일 |
| http_req_failed | 1.49% | 1.39% | -0.10%p |
| 서버 에러율 (checks_failed) | (미분리) | **0%** | — |

- TPS 6% 상승 = PHOTO 20% 400 거절(비효율적 거절이 오히려 스루풋을 깎아먹은 효과) 제거 영향
- 서버 에러율은 어제도 사실 0%였을 가능성 높음 — 당시에는 `http_req_failed`에 duplicate + PHOTO가 섞여 있어 구분 불가

## 원본 로그

- `results/raw/03c_saturation.{log,json}`
