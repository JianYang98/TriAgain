# 부하테스트 가데이터 SQL 스크립트

## 단계별 데이터 규모

| 단계 | 크루 수 | 크루당 멤버 | 유저 수 | 챌린지 건수 |
|------|---------|-----------|---------|-----------|
| S    | 10      | 5         | 50      | 50        |
| M    | 50      | 5         | 250     | 250       |
| L    | 200     | 5         | 1,000   | 1,000     |
| XL   | 500     | 5         | 2,500   | 2,500     |
| XXL  | 2,000   | 5         | 10,000  | 10,000    |

- **XXL**은 `load-write-heavy.js` (Day 7) 쓰기 TPS 측정 전용 —
  매 iter마다 유효 INSERT가 되려면 (user_id, crew_id, target_date) UNIQUE 제약상
  유저 풀 크기 = 가능한 유효 INSERT 수. 2분 × 80 TPS ≈ 9,600건 소화 가능.

## 실행 방법

### 초기 세팅 (01~06 순서대로, FK 의존성)

```bash
# 1. 단계 파라미터 설정 (S/M/L/XL/XXL)
export SCALE=S

# 2. 초기화 (기존 loadtest 데이터 삭제)
psql -h <HOST> -U <USER> -d <DB> -f 00_truncate.sql

# 3. 순서대로 실행 (01~06은 FK 의존성 때문에 반드시 번호 순)
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 01_users.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 02_crews.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 03_crew_members.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 04_challenges_api.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 05_challenges_scheduler.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 06_notifications.sql
```

### 러시 테스트 전용 (07)

```bash
# 초기 1회 (러시 크루 생성)
psql ... -f 07_rush_crews.sql

# crew-rush.js 실행 사이마다 (참여한 멤버/챌린지 롤백 + 가입 상태 복구)
psql ... -f 07_rush_reset.sql

# 크루를 통째로 재빌드할 때만 (파괴적) → teardown 후 다시 생성
psql ... -f 07_rush_teardown.sql
psql ... -f 07_rush_crews.sql
```

### API 테스트 반복용 (08)

```bash
# k6 write 시나리오(load-peak.js, load-normal.js 등) 실행 사이마다
# verify_duplicate(409)를 제거하고 챌린지를 미인증 초기 상태로 복구
psql ... -f 08_reset_api_verifications.sql
```

## 파일 설명

| 파일 | 용도 | 실행 타이밍 |
|------|------|------------|
| `00_truncate.sql` | loadtest- prefix 데이터만 삭제 (기존 유저 보존) | 초기 1회 |
| `01_users.sql` | 테스트 유저 생성 (loadtest-user-1 ~ N) | 초기 1회 |
| `02_crews.sql` | ACTIVE 크루 생성 (loadtest-crew-1 ~ N) | 초기 1회 |
| `03_crew_members.sql` | 유저-크루 매핑 (크루당 5명) | 초기 1회 |
| `04_challenges_api.sql` | API 테스트용 챌린지 (오늘 마감, 미인증 → POST /verifications 가능) | 초기 1회 (날짜 변경 시 재실행) |
| `05_challenges_scheduler.sql` | 스케줄러 테스트용 (어제 마감, 미인증 → FailExpiredChallengesScheduler 대상) | 초기 1회 |
| `06_notifications.sql` | 알림 조회 테스트용 (유저당 10개) | 초기 1회 |
| `07_rush_crews.sql` | 러시 테스트 전용 크루 (max_members=10) | 러시 테스트 초기 1회 |
| `07_rush_reset.sql` | 러시 크루 멤버/챌린지 롤백 + 상태(ACTIVE/late_join) 복구 (비파괴) | `crew-rush.js` 실행 사이마다 |
| `07_rush_teardown.sql` | 러시 크루 행까지 통째 삭제 (파괴적) → 이후 `07_rush_crews.sql` 재생성 | 크루 재빌드 필요 시만 |
| `08_reset_api_verifications.sql` | 오늘자 인증 삭제 + API 챌린지 초기화 | k6 write 시나리오 실행 사이마다 |
| `09_sched_activate.sql` | ActivateRecruitingCrews 대상 (RECRUITING 크루) | 스케줄러 측정 전 |
| `09_sched_complete.sql` | CompleteExpiredCrews 대상 (만료 ACTIVE 크루 + IN_PROGRESS 챌린지) | 스케줄러 측정 전 |
| `09_sched_expire_session.sql` | ExpireUploadSession 대상 (PENDING 업로드 세션) | 스케줄러 측정 전 |
| `09_sched_reminder.sql` | Reminder 대상 (마감 임박 + 미인증 멤버) | 스케줄러 측정 전 (30분 이내 트리거) |
| `09_sched_start_noti.sql` | CrewStartNotification 대상 (오늘 시작 크루) | 스케줄러 측정 전 (당일만 유효) |
| `09_sched_reset.sql` | 09_sched_* 데이터 일괄 삭제 | 재측정 전 |

## Prefix 매트릭스 (충돌 방지)

테스트 데이터가 prefix로 격리되어 있어 서로 간섭하지 않는다.

| Prefix | 생성 스크립트 | 리셋 스크립트 | 용도 |
|--------|--------------|--------------|------|
| `loadtest-crew-*`       | `02_crews.sql`       | `08_reset_api_verifications.sql` | API read/write 테스트 |
| `loadtest-sched-crew-*` | `05_challenges_scheduler.sql` | (없음 — 스케줄러 테스트 전 재생성) | fail-expired 스케줄러 |
| `loadtest-sched-recruit-*` | `09_sched_activate.sql` | `09_sched_reset.sql` | activate 스케줄러 |
| `loadtest-sched-complete-*` | `09_sched_complete.sql` | `09_sched_reset.sql` | complete 스케줄러 |
| `loadtest-sched-remind-*` | `09_sched_reminder.sql` | `09_sched_reset.sql` | reminder 스케줄러 |
| `loadtest-sched-start-*` | `09_sched_start_noti.sql` | `09_sched_reset.sql` | start_noti 스케줄러 |
| `loadtest-sched-upload/*` | `09_sched_expire_session.sql` | `09_sched_reset.sql` | expire_session 스케줄러 |
| `loadtest-rush-crew-*`  | `07_rush_crews.sql`  | `07_rush_reset.sql`  | 크루 참가 동시성 테스트 |

- `LIKE 'loadtest-crew-%'` 필터는 다른 prefix를 매치하지 않는다 (접두사 분리)
- 각 리셋 스크립트는 자기 prefix만 건드리므로 다른 테스트 데이터 오염 없음
- `09_sched_reset.sql`은 05번(fail-expired) 데이터를 건드리지 않음

### invite_code 매트릭스

| Prefix | 스크립트 | 범위 |
|--------|---------|------|
| `SC` | `05_challenges_scheduler.sql` | SC0001~ (fail-expired 크루) |
| `SA` | `09_sched_activate.sql` | SA0001~ |
| `CC` | `09_sched_complete.sql` | CC0001~ |
| `SR` | `09_sched_reminder.sql` | SR0001~ |
| `SS` | `09_sched_start_noti.sql` | SS0001~ |

## 스케줄러 전체 측정 (09_sched)

```bash
# 선행: 01_users.sql 완료 (유저 존재해야 함)

# 1. 리셋 (이전 데이터 정리)
psql ... -f 09_sched_reset.sql

# 2. 각 스케줄러 데이터 생성 (독립 — 순서 무관)
psql ... -f 09_sched_activate.sql
psql ... -f 09_sched_complete.sql
psql ... -f 09_sched_expire_session.sql
psql ... -f 09_sched_reminder.sql       # 30분 이내 트리거 필요
psql ... -f 09_sched_start_noti.sql     # 당일만 유효

# 3. 스케줄러 트리거 (Internal API)
curl -X POST http://<host>/internal/scheduler/activate-crews \
  -H 'X-Internal-Api-Key: loadtest-internal-key'

curl -X POST http://<host>/internal/scheduler/complete-crews \
  -H 'X-Internal-Api-Key: loadtest-internal-key'

# ... (각 스케줄러별)

# 4. 스케일 변경: 각 SQL 상단 \set crew_count / session_count 수정
```

## 주의사항

- 01~06은 반드시 번호 순서대로 실행 (FK 의존성)
- 07, 08은 각자 독립적으로 반복 실행 가능 (idempotent)
- 09는 서로 독립적, 순서 무관 (각자 자기 prefix 데이터만 관리)
- `04_challenges_api.sql`의 챌린지는 당일 기준이므로, 날짜가 바뀌면 재생성 필요
- `09_sched_reminder.sql`은 실행 후 30분 이내에 스케줄러 트리거해야 함 (deadline_time 동적)
- `09_sched_start_noti.sql`은 당일만 유효 (start_date = CURRENT_DATE)
