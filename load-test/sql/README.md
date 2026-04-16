# 부하테스트 가데이터 SQL 스크립트

## 단계별 데이터 규모

| 단계 | 크루 수 | 크루당 멤버 | 유저 수 | 챌린지 건수 |
|------|---------|-----------|---------|-----------|
| S    | 10      | 5         | 50      | 50        |
| M    | 50      | 5         | 250     | 250       |
| L    | 200     | 5         | 1,000   | 1,000     |
| XL   | 500     | 5         | 2,500   | 2,500     |

## 실행 방법

### 초기 세팅 (01~06 순서대로, FK 의존성)

```bash
# 1. 단계 파라미터 설정 (S/M/L/XL)
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

# crew-rush.js 실행 사이마다 (참여한 멤버/챌린지 롤백)
psql ... -f 07_rush_reset.sql
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
| `07_rush_reset.sql` | 러시 크루 멤버/챌린지 롤백 | `crew-rush.js` 실행 사이마다 |
| `08_reset_api_verifications.sql` | 오늘자 인증 삭제 + API 챌린지 초기화 | k6 write 시나리오 실행 사이마다 |

## Prefix 매트릭스 (충돌 방지)

세 종류의 테스트 데이터가 prefix로 격리되어 있어 서로 간섭하지 않는다.

| Prefix | 생성 스크립트 | 리셋 스크립트 | 용도 |
|--------|--------------|--------------|------|
| `loadtest-crew-*`       | `02_crews.sql`       | `08_reset_api_verifications.sql` | API read/write 테스트 |
| `loadtest-sched-crew-*` | `05_challenges_scheduler.sql` | (없음 — 스케줄러 테스트 전 재생성) | 스케줄러 테스트 |
| `loadtest-rush-crew-*`  | `07_rush_crews.sql`  | `07_rush_reset.sql`  | 크루 참가 동시성 테스트 |

- `LIKE 'loadtest-crew-%'` 필터는 `loadtest-sched-crew-*`, `loadtest-rush-crew-*`를 매치하지 않는다 (접두사 분리)
- 각 리셋 스크립트는 자기 prefix만 건드리므로 다른 테스트 데이터 오염 없음

## 주의사항

- 01~06은 반드시 번호 순서대로 실행 (FK 의존성)
- 07, 08은 각자 독립적으로 반복 실행 가능 (idempotent)
- `04_challenges_api.sql`의 챌린지는 당일 기준이므로, 날짜가 바뀌면 재생성 필요
