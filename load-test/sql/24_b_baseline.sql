-- ============================================================
-- B 테스트 run 전 기준선 (읽기 전용) — 결과를 sql-before.txt로 보존
-- 사용법: psql -X -v ON_ERROR_STOP=1 -f 24_b_baseline.sql > sql-before.txt
-- 변수 없음. 앱 기동 후 다시 실행해 기동 보정 러너·스케줄러가 dbb- 크루를 건드리지 않았는지 본다
-- (scheduler_candidates 기대 0, 크루 status는 RECRUITING 그대로).
-- ============================================================
\set ON_ERROR_STOP on
SET default_transaction_read_only = on;

SELECT 'version' AS key, version() AS value
UNION ALL SELECT 'timezone', current_setting('TimeZone')
UNION ALL SELECT 'now', NOW()::text
UNION ALL SELECT 'pool_users', (SELECT count(*) FROM users WHERE id LIKE 'loadtest-user-%')::text
UNION ALL SELECT 'leader_exists', (SELECT count(*) = 1 FROM users WHERE id = 'loadtest-leader')::text
UNION ALL SELECT 'dbb_crews', (SELECT count(*) FROM crews WHERE id LIKE 'dbb-%')::text
UNION ALL SELECT 'scheduler_candidates',  -- 기대 0
    (SELECT count(*) FROM crews WHERE id LIKE 'dbb-%' AND status = 'RECRUITING' AND start_date <= CURRENT_DATE)::text
UNION ALL SELECT 'dbb_member_rows',
    (SELECT count(*) FROM crew_members WHERE crew_id LIKE 'dbb-%')::text;  -- 크루 행 없는 잔재도 센다

SELECT c.id, c.status, c.start_date, c.end_date, c.max_members, c.current_members,
       (SELECT count(*) FROM crew_members m WHERE m.crew_id = c.id) AS member_rows
FROM crews c WHERE c.id LIKE 'dbb-%'
ORDER BY c.id;
