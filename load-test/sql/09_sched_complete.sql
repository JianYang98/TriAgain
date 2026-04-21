-- =============================================================
-- 09_sched_complete.sql — CompleteExpiredCrewsScheduler
-- =============================================================
-- 타겟 조건: crews.status = 'ACTIVE' AND end_date < CURRENT_DATE
-- 스케줄러 동작: ACTIVE → COMPLETED + IN_PROGRESS 챌린지 → ENDED
-- 선행: 01_users.sql 실행 완료
-- =============================================================

-- ===== SCALE CONFIG =====
-- S=10, M=50, L=200, XL=500  (멤버/챌린지수 = crew_count × 5)
\set crew_count 10
\set user_count 50
-- ========================

BEGIN;

-- 기존 데이터 정리
DELETE FROM challenges WHERE crew_id LIKE 'loadtest-sched-complete-%';
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-sched-complete-%';
DELETE FROM crews WHERE id LIKE 'loadtest-sched-complete-%';

-- 크루 생성 (ACTIVE 상태, 종료일 이미 지남)
INSERT INTO crews (id, creator_id, name, goal, verification_content, verification_type,
                   min_members, max_members, current_members, status,
                   start_date, end_date, allow_late_join, invite_code, created_at,
                   deadline_time, category, visibility)
SELECT
    'loadtest-sched-complete-crew-' || LPAD(n::text, 4, '0'),
    'loadtest-user-' || ((n - 1) % :user_count + 1),
    '스케줄러테스트-종료-' || n,
    '종료 테스트용 크루',
    '테스트 인증',
    'TEXT',
    1, 10, 5, 'ACTIVE',
    CURRENT_DATE - 8,
    CURRENT_DATE - 1,
    true,
    'CC' || LPAD(n::text, 4, '0'),
    CURRENT_TIMESTAMP - INTERVAL '10 days',
    '23:59:59',
    'ETC',
    'PUBLIC'
FROM generate_series(1, :crew_count) AS n;

-- 크루 멤버 (크루당 5명: LEADER 1 + MEMBER 4)
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-sc-' || LPAD(n::text, 4, '0') || '-' || m,
    'loadtest-user-' || (((n - 1) * 5 + m - 1) % :user_count + 1),
    'loadtest-sched-complete-crew-' || LPAD(n::text, 4, '0'),
    CASE WHEN m = 1 THEN 'LEADER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - INTERVAL '10 days'
FROM generate_series(1, :crew_count) AS n
CROSS JOIN generate_series(1, 5) AS m;

-- 챌린지 (크루당 5개 = 멤버당 1개, IN_PROGRESS → 스케줄러가 ENDED로 전환)
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days,
                        completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-sc-' || LPAD(n::text, 4, '0') || '-' || m,
    'loadtest-user-' || (((n - 1) * 5 + m - 1) % :user_count + 1),
    'loadtest-sched-complete-crew-' || LPAD(n::text, 4, '0'),
    1,
    3,
    1,
    'IN_PROGRESS',
    CURRENT_DATE - 3,
    (CURRENT_DATE - 2)::timestamp + TIME '23:59:59',
    CURRENT_TIMESTAMP - INTERVAL '3 days'
FROM generate_series(1, :crew_count) AS n
CROSS JOIN generate_series(1, 5) AS m;

COMMIT;

-- 확인
SELECT count(*) AS active_crews
FROM crews WHERE id LIKE 'loadtest-sched-complete-%' AND status = 'ACTIVE';
SELECT count(*) AS members
FROM crew_members WHERE crew_id LIKE 'loadtest-sched-complete-%';
SELECT count(*) AS in_progress_challenges
FROM challenges WHERE crew_id LIKE 'loadtest-sched-complete-%' AND status = 'IN_PROGRESS';
