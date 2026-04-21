-- =============================================================
-- 09_sched_reset.sql — 스케줄러 테스트 데이터 전체 리셋
-- =============================================================
-- 용도: 09_sched_*.sql로 생성한 데이터 일괄 삭제 (재측정 전 초기화)
-- 실행: psql -h <host> -U <user> -d <db> -f 09_sched_reset.sql
-- 주의: 05_challenges_scheduler.sql (loadtest-sched-crew-*) 데이터는 건드리지 않음
-- =============================================================

BEGIN;

-- FK 참조 역순으로 삭제

-- 1. challenges (complete 크루의 IN_PROGRESS 챌린지)
DELETE FROM challenges WHERE crew_id LIKE 'loadtest-sched-complete-%';

-- 2. notifications (reminder, start_noti 스케줄러가 생성한 알림)
DELETE FROM notifications WHERE target_id LIKE 'loadtest-sched-recruit-%'
                             OR target_id LIKE 'loadtest-sched-complete-%'
                             OR target_id LIKE 'loadtest-sched-remind-%'
                             OR target_id LIKE 'loadtest-sched-start-%';

-- 3. dead_letters (스케줄러 실패 시 생성된 DLQ)
DELETE FROM dead_letters WHERE target_id LIKE 'loadtest-sched-recruit-%'
                            OR target_id LIKE 'loadtest-sched-complete-%'
                            OR target_id LIKE 'loadtest-sched-remind-%'
                            OR target_id LIKE 'loadtest-sched-start-%';

-- 4. upload_session (expire_session 데이터)
DELETE FROM upload_session WHERE image_key LIKE 'loadtest-sched-upload/%';

-- 5. crew_members (09_sched 전용 크루)
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-sched-recruit-%'
                            OR crew_id LIKE 'loadtest-sched-complete-%'
                            OR crew_id LIKE 'loadtest-sched-remind-%'
                            OR crew_id LIKE 'loadtest-sched-start-%';

-- 6. crews (09_sched 전용 크루)
DELETE FROM crews WHERE id LIKE 'loadtest-sched-recruit-%'
                     OR id LIKE 'loadtest-sched-complete-%'
                     OR id LIKE 'loadtest-sched-remind-%'
                     OR id LIKE 'loadtest-sched-start-%';

COMMIT;

-- 확인
SELECT 'crews' AS table_name, count(*) AS remaining
FROM crews WHERE id LIKE 'loadtest-sched-recruit-%'
             OR id LIKE 'loadtest-sched-complete-%'
             OR id LIKE 'loadtest-sched-remind-%'
             OR id LIKE 'loadtest-sched-start-%'
UNION ALL
SELECT 'crew_members', count(*)
FROM crew_members WHERE crew_id LIKE 'loadtest-sched-recruit-%'
                     OR crew_id LIKE 'loadtest-sched-complete-%'
                     OR crew_id LIKE 'loadtest-sched-remind-%'
                     OR crew_id LIKE 'loadtest-sched-start-%'
UNION ALL
SELECT 'challenges', count(*)
FROM challenges WHERE crew_id LIKE 'loadtest-sched-complete-%'
UNION ALL
SELECT 'upload_session', count(*)
FROM upload_session WHERE image_key LIKE 'loadtest-sched-upload/%'
UNION ALL
SELECT 'notifications', count(*)
FROM notifications WHERE target_id LIKE 'loadtest-sched-recruit-%'
                      OR target_id LIKE 'loadtest-sched-complete-%'
                      OR target_id LIKE 'loadtest-sched-remind-%'
                      OR target_id LIKE 'loadtest-sched-start-%';
