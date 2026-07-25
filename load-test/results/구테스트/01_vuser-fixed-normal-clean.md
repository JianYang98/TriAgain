# VUser 고정 테스트 결과 (평상시 A:B = 90:10) — 깨끗한 재측정

- 일시: 2026-04-16 (Day 2)
- 서버: EC2 t2.micro (1 vCPU, 1GB RAM)
- 프로필: prod,loadtest
- 데이터: S단계 (유저 50, 크루 10 — 전부 TEXT 인증)
- 스크립트: load-normal.js (TARGET_VUS, DURATION=2m)
- 사전 조건: 10 VU 1분 warm-up 완료, 매 VU 테스트 전 `08_reset_api_verifications.sql` 실행
- 원본: `results/01_vuser-fixed-normal.md` (오염된 데이터)
- 재측정 배경: `03a_rerun-with-reset.md`

## 판정 기준 (재정의)

- p95 A < 200ms, p95 B < 500ms
- **서버 에러율 = `checks_failed` / `checks_total`** (201/409 외 응답만 서버 에러로 간주)
- `http_req_failed`는 duplicate 409 포함이므로 판정에서 제외

## 결과

| VU | TPS | p95 (A 읽기) | p95 (B 쓰기) | verify_created | 서버 에러율 | 판정 |
|----|-----|-------------|-------------|----------------|-------------|------|
| 10 | 662/s | 18ms | 23ms | 50 / 50 | 0% | ✅ PASS (여유) |
| 30 | 920/s | 54ms | 74ms | 50 / 50 | 0% | ✅ PASS (여유) |
| 50 | 919/s | 99ms | 113ms | 50 / 50 | 0% | ✅ PASS (포화 시작) |

## 분석

- **TPS 포화점**: VU 30~50 구간에서 ~920/s 수렴 — 이 이상 VU를 올려도 TPS는 더 이상 증가하지 않음 (`03c_stress-breaking-point.md` 참조)
- **p95 여유도**: VU 50에서도 p95 A 99ms (기준 200ms의 절반). 절대값 여유 큼
- **최적 운영점**: VU 50 — 기준 내에서 TPS 최대
- **verify_created 50/50 전 조합 성공** — PHOTO 오염 제거 + 08 리셋 덕분에 반복 가능한 측정 확보

## 어제(오염) vs 오늘(깨끗) 비교

| VU | 어제 TPS | 오늘 TPS | 어제 p95 A | 오늘 p95 A | 어제 에러율 | 오늘 서버 에러율 |
|----|---------|---------|-----------|-----------|-------------|-----------------|
| 10 | 386/s   | 662/s   | 42ms      | 18ms      | 0.17%       | 0%              |
| 30 | 784/s   | 920/s   | 64ms      | 54ms      | 0.25%       | 0%              |
| 50 | 872/s   | 919/s   | 103ms     | 99ms      | 0.34%       | 0%              |

- 어제 VU 10 TPS가 낮은 건 테스트 시작 직후 JVM 콜드 스타트 영향 + PHOTO 20% 거절(400)이 섞인 복합 원인
- 어제 "에러율"은 전부 비즈니스 거절(PHOTO 400 + duplicate 409) — 서버 에러는 당시에도 실제 0%였음

## 원본 로그

- `results/raw/03c_vufixed-10.{log,json}`
- `results/raw/03c_vufixed-30.{log,json}`
- `results/raw/03c_vufixed-50.{log,json}`
