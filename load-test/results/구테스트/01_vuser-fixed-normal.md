> ⚠️ **주의 — 오염된 측정 결과**
>
> 이 수치는 다음 두 문제로 오염된 상태입니다. 깨끗한 재측정 결과는 [`01_vuser-fixed-normal-clean.md`](./01_vuser-fixed-normal-clean.md)를 참고하세요.
>
> - **PHOTO 20% 오염**: `02_crews.sql`이 20% 크루를 PHOTO로 생성했고, k6 `writeScenario`는 TEXT만 보내 PHOTO 크루는 매번 400 PHOTO_REQUIRED 반환 → `http_req_failed`·TPS 왜곡
> - **verify_duplicate 누적**: 반복 실행 시 UNIQUE(user_id,crew_id,target_date)에 막혀 409가 계속 쌓이는 상태
>
> 수정 내역·원인 분석: [`03a_rerun-with-reset.md`](./03a_rerun-with-reset.md)

# VUser 고정 테스트 결과 (평상시 A:B = 90:10)

- 일시: 2026-04-16
- 서버: EC2 t2.micro (1 vCPU, 1GB RAM)
- 프로필: prod,loadtest
- 데이터: S단계 (유저 50, 크루 10)
- 스크립트: load-normal.js (TARGET_VUS, DURATION=2m)

## 결과

| VU | TPS | p95 (A 읽기) | p95 (B 쓰기) | 에러율 | 판정 |
|----|-----|-------------|-------------|--------|------|
| 10 | 386/s | 42ms | 98ms | 0.17% | PASS |
| 30 | 784/s | 64ms | 88ms | 0.25% | PASS |
| 50 | 872/s | 103ms | 113ms | 0.34% | PASS |
| 100 | 898/s | 237ms | 255ms | 0.60% | FAIL (p95>200) |
| 150 | 879/s | 353ms | 372ms | 0.90% | FAIL (p95>200) |

## 판정 기준

- p95 < 200ms, p99 < 500ms, 에러율 < 1%

## 분석

- TPS는 50~100 VU 사이에서 포화 (~870-900/s 수렴)
- 50 VU가 최적점: p95 기준(200ms) 내에서 TPS 최대
- 100 VU부터 p95 초과, TPS는 안 올라가고 응답만 느려짐
