# 스케줄러 단계별 테스트 결과 (S → M → L → XL)

- 일시: 2026-04-16 (Day 6)
- 서버: EC2 t3.micro (2 vCPU burstable, 1GB RAM) — 자세한 환경은 `00_environment.md` 참조
- 프로필: `prod,loadtest`
- 스크립트: `scheduler-progression.sh` (각 스케일마다 `loadtest-sched-*` 데이터 초기화 + 재생성 + 단독 트리거)
- 트리거 엔드포인트: `POST /internal/scheduler/{name}` (loadtest 프로필 전용, `X-Internal-Api-Key` 필요)

## 판정 기준

- **PASS**: Duration < 300,000ms (5분 윈도우)
- **실 운영 경고선**: 60,000ms (1분) — 초과 시 스케일업/튜닝 고려
- 5개 스케줄러는 테스트 데이터 조건상 대상이 없는 상태로 측정됨 (아래 "한계" 참조)

## 결과 매트릭스 (Duration ms)

| 스케줄러 | S (50) | M (250) | L (1000) | XL (2500) | 판정 |
|---------|--------|---------|----------|-----------|------|
| **`fail-expired`** | **39** | **180** | **612** | **1,420** | ✅ PASS (전부) |
| `activate-crews` | 2 | 1 | 1 | 1 | ✅ PASS (대상 없음) |
| `complete-crews` | 1 | 1 | 1 | 1 | ✅ PASS (대상 없음) |
| `send-reminders` | 3 | 2 | 2 | 7 | ✅ PASS (대상 없음) |
| `crew-start-notifications` | 9 | 2 | 2 | 6 | ✅ PASS (대상 없음) |
| `expire-upload-sessions` | 1 | 1 | 1 | 1 | ✅ PASS (대상 없음) |

**모든 스케줄러가 5분 윈도우 대비 대폭 여유.** 최장인 `fail-expired XL`(2,500건 처리)도 1.4초.

## 핵심 해석

### `fail-expired`는 선형 스케일링 — 예측 가능한 부하

| 스케일 | 챌린지 | Duration | 챌린지당 |
|-------|--------|----------|---------|
| S | 50 | 39ms | 0.78ms |
| M | 250 | 180ms | 0.72ms |
| L | 1,000 | 612ms | 0.61ms |
| XL | 2,500 | 1,420ms | 0.57ms |

- **36배 스케일업(50→2500)에 36배 Duration(39→1420ms)** — 거의 선형. 숨은 N+1/인덱스 누락 없음
- 챌린지당 처리 시간이 스케일 증가에도 **약간 줄어드는** 경향 → 배치 처리 효율성 (connection reuse, plan 캐시)
- XL (2,500건 = 전체 사용자 규모)에서도 1.4초 → 실 운영 (Phase 1: 500명) 규모에서 무시할 수준

### 나머지 5개는 "오버헤드 하한선"

모두 1~9ms. 대상 0건 조회 쿼리 + 로깅만 수행한 오버헤드 수준. 실 부하 측정 아님.

## ⚠️ 한계 / 측정 범위 명시

### 테스트 데이터 조건 불일치

`05_challenges_scheduler.sql`은 **`fail-expired` 트리거 대상만 생성**하도록 설계됐다:
- 크루: `start_date = CURRENT_DATE - 3`, `end_date = CURRENT_DATE + 3`, `status = 'ACTIVE'`
- 챌린지: `status = 'IN_PROGRESS'`, `deadline = (CURRENT_DATE - 1) + '23:59:59'` (어제 마감)

각 스케줄러의 대상 조건 vs 실제 데이터:

| 스케줄러 | 대상 조건 | 테스트 데이터 | 결과 |
|---------|----------|--------------|------|
| `fail-expired` | deadline 지난 IN_PROGRESS | 전부 일치 | ✅ **실측됨** |
| `activate-crews` | `status = RECRUITING`, `start_date <= today` | 전부 ACTIVE 상태 | ❌ 대상 0 |
| `complete-crews` | `end_date < today` | end_date 미래 | ❌ 대상 0 |
| `send-reminders` | 오늘 마감 임박 | deadline 어제 | ❌ 대상 0 |
| `crew-start-notifications` | 오늘 시작 크루 | start_date 3일 전 | ❌ 대상 0 |
| `expire-upload-sessions` | PENDING UploadSession | UploadSession 자체 없음 | ❌ 대상 0 |

### 이번 측정으로 증명된 것 / 안 된 것

✅ **증명됨**:
- `fail-expired`는 XL(2,500건) 규모에서도 5분 윈도우 내 완료
- `fail-expired`는 선형 스케일링 (인덱스 누락/N+1 없음)
- 6개 스케줄러 "오버헤드 하한선" (대상 0일 때): 1~9ms

❌ **이번 측정으로 증명되지 않은 것**:
- `activate-crews` / `complete-crews` / `send-reminders` / `crew-start-notifications` / `expire-upload-sessions` 가 **실제 부하 걸렸을 때** XL에서 5분 내 완료되는지
- 이를 증명하려면 각 스케줄러별 대상 조건에 맞는 SQL 스크립트(`07_sched_activate_data.sql` 등) 신설 + 재측정 필요 → **future work**

### 환경 전제

- `application-loadtest.yml`에서 `firebase.enabled` 미설정 → **FCM NoOp 어댑터** 사용
- 즉 `send-reminders`, `crew-start-notifications`는 실측 대상이 있었더라도 FCM 네트워크 비용이 포함되지 않은 측정이 됐을 것
- 실 운영에서는 Firebase 배치 API 호출 비용(수십~수백 ms/배치) 추가 고려 필요

## 발견 + 수정 사항

- `SchedulerTriggerController`(`/internal/scheduler/*`)는 `X-Internal-Api-Key` 헤더 필수 (application-loadtest.yml:15 = `loadtest-internal-key`)
- Day 2 세션에서 스크립트 신설 후 실제 실행은 Day 6가 최초 → `scheduler-test.sh` / `concurrent-test.sh` 에 헤더 누락 발견, 두 스크립트 모두 수정

## 원본 로그

- `results/raw/day6_sched_fail-expired.log`
- `results/raw/day6_sched_activate-crews.log`
- `results/raw/day6_sched_complete-crews.log`
- `results/raw/day6_sched_send-reminders.log`
- `results/raw/day6_sched_crew-start-notifications.log`
- `results/raw/day6_sched_expire-upload-sessions.log`
- `results/raw/day6_sched_all.log` (통합)
