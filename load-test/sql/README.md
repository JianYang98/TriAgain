# 부하테스트 가데이터 SQL 스크립트

## 단계별 데이터 규모

| 단계 | 크루 수 | 크루당 멤버 | 유저 수 | 챌린지 건수 |
|------|---------|-----------|---------|-----------|
| S    | 10      | 5         | 50      | 50        |
| M    | 50      | 5         | 250     | 250       |
| L    | 200     | 5         | 1,000   | 1,000     |
| XL   | 500     | 5         | 2,500   | 2,500     |

## 실행 방법

```bash
# 1. 단계 파라미터 설정 (S/M/L/XL)
export SCALE=S

# 2. 초기화 (기존 loadtest 데이터 삭제)
psql -h <HOST> -U <USER> -d <DB> -f 00_truncate.sql

# 3. 순서대로 실행
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 01_users.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 02_crews.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 03_crew_members.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 04_challenges_api.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 05_challenges_scheduler.sql
psql -h <HOST> -U <USER> -d <DB> -c "SET app.scale='$SCALE';" -f 06_notifications.sql
```

## 파일 설명

| 파일 | 설명 |
|------|------|
| `00_truncate.sql` | loadtest- prefix 데이터만 삭제 (기존 유저 보존) |
| `01_users.sql` | 테스트 유저 생성 (loadtest-user-1 ~ N) |
| `02_crews.sql` | ACTIVE 크루 생성 (loadtest-crew-1 ~ N) |
| `03_crew_members.sql` | 유저-크루 매핑 (크루당 5명) |
| `04_challenges_api.sql` | API 테스트용 챌린지 (오늘 마감, 미인증 → POST /verifications 가능) |
| `05_challenges_scheduler.sql` | 스케줄러 테스트용 (어제 마감, 미인증 → FailExpiredChallengesScheduler 대상) |
| `06_notifications.sql` | 알림 조회 테스트용 (유저당 10개) |

## 주의사항

- 반드시 번호 순서대로 실행 (FK 의존성)
- API 테스트와 스케줄러 테스트 데이터는 별도 크루 사용 (충돌 방지)
- `04_challenges_api.sql`의 챌린지는 당일 기준이므로, 날짜가 바뀌면 재생성 필요
