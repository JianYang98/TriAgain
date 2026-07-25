> ⚠️ **주의 — 오염된 측정 결과**
>
> 이 수치는 다음 두 문제로 오염된 상태입니다. 깨끗한 재측정 결과는 [`02_saturation-clean.md`](./02_saturation-clean.md)를 참고하세요.
>
> - **PHOTO 20% 오염**: `02_crews.sql`이 20% 크루를 PHOTO로 생성했고, k6 `writeScenario`는 TEXT만 보내 PHOTO 크루는 매번 400 PHOTO_REQUIRED 반환 → `http_req_failed`·TPS 왜곡
> - **verify_duplicate 누적**: 반복 실행 시 UNIQUE(user_id,crew_id,target_date)에 막혀 409가 계속 쌓이는 상태
>
> 수정 내역·원인 분석: [`03a_rerun-with-reset.md`](./03a_rerun-with-reset.md)

# 포화점 탐색 (Saturation Test) 결과

- 일시: 2026-04-16
- 서버: EC2 t2.micro (1 vCPU, 1GB RAM)
- 프로필: prod,loadtest
- 데이터: S단계 (유저 50, 크루 10)
- 스크립트: saturation.js (VU 10->300, 10분, A:B = 90:10)

## 전체 결과

| 지표 | 결과 | 기준 | 판정 |
|------|------|------|------|
| p95 (A 읽기) | 449ms | <200ms | FAIL |
| p95 (B 쓰기) | 564ms | <500ms | FAIL |
| 에러율 | 1.49% | <1% | FAIL |
| 평균 TPS | 780/s | - | - |
| max VU | 320 | - | - |

## 상세 수치

| 지표 | avg | med | p90 | p95 | max |
|------|-----|-----|-----|-----|-----|
| A (읽기) | 138ms | 79ms | 333ms | 449ms | 3.03s |
| B (쓰기) | 205ms | 150ms | 452ms | 564ms | 1.91s |

## 포화점 분석 (VUser 고정 테스트 + Saturation 종합)

| VU | TPS | p95 (A) | 상태 |
|----|-----|---------|------|
| 10 | 386/s | 42ms | 여유 |
| 30 | 784/s | 64ms | 여유 |
| **50** | **872/s** | **103ms** | **최적점 (TPS 포화 시작, p95 기준 내)** |
| 100 | 898/s | 237ms | p95 초과 |
| 150 | 879/s | 353ms | TPS 하락 시작 |
| 300 | 780/s | 449ms | Breaking Point 영역 |

- 포화점 (Saturation Point): ~50 VU / TPS ~870/s
- Breaking Point: ~100-150 VU (p95 초과 + TPS 하락 시작)
- 300 VU에서도 서버 다운 없이 버팀 (에러율 1.49%)
