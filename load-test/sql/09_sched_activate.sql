-- =============================================================
-- 09_sched_activate.sql — ActivateRecruitingCrewsScheduler
-- =============================================================
-- 타겟 조건: crews.status = 'RECRUITING' AND start_date <= CURRENT_DATE
-- 스케줄러 동작: RECRUITING → ACTIVE 전환
-- 선행: 01_users.sql 실행 완료 (user_count 이상의 유저 필요)
-- =============================================================

-- ===== SCALE CONFIG =====
-- S=50, M=250, L=1000, XL=2500
\set crew_count 50
\set user_count 50
-- ========================

BEGIN;

-- 기존 데이터 정리
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-sched-recruit-%';
DELETE FROM crews WHERE id LIKE 'loadtest-sched-recruit-%';

-- 크루 생성 (RECRUITING 상태, 시작일 이미 지남)
INSERT INTO crews (id, creator_id, name, goal, verification_content, verification_type,
                   min_members, max_members, current_members, status,
                   start_date, end_date, allow_late_join, invite_code, created_at,
                   deadline_time, category, visibility)
SELECT
    'loadtest-sched-recruit-crew-' || LPAD(n::text, 4, '0'),
    'loadtest-user-' || ((n - 1) % :user_count + 1),
    '스케줄러테스트-활성화-' || n,
    '활성화 테스트용 크루',
    '테스트 인증',
    'TEXT',
    1, 10, 1, 'RECRUITING',
    CURRENT_DATE - 1,
    CURRENT_DATE + 5,
    true,
    'SA' || LPAD(n::text, 4, '0'),
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    '23:59:59',
    'ETC',
    'PUBLIC'
FROM generate_series(1, :crew_count) AS n;

-- 크루 멤버 (크루당 LEADER 1명)
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-sr-' || LPAD(n::text, 4, '0'),
    'loadtest-user-' || ((n - 1) % :user_count + 1),
    'loadtest-sched-recruit-crew-' || LPAD(n::text, 4, '0'),
    'LEADER',
    CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM generate_series(1, :crew_count) AS n;

COMMIT;

-- 확인
SELECT count(*) AS recruiting_crews
FROM crews WHERE id LIKE 'loadtest-sched-recruit-%' AND status = 'RECRUITING';
SELECT count(*) AS members
FROM crew_members WHERE crew_id LIKE 'loadtest-sched-recruit-%';
