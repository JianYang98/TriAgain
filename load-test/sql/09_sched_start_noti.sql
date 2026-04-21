-- =============================================================
-- 09_sched_start_noti.sql — CrewStartNotificationScheduler
-- =============================================================
-- 타겟 조건: crews.status = 'ACTIVE' AND start_date = CURRENT_DATE
-- 스케줄러 동작: 오늘 시작 크루 멤버에게 시작 알림 발송
-- 주의: CURRENT_DATE 기반 — 실행 당일만 유효, 날짜 바뀌면 재생성 필요
-- 선행: 01_users.sql 실행 완료
-- =============================================================

-- ===== SCALE CONFIG =====
-- S=10, M=50, L=200, XL=500  (멤버수 = crew_count × 5)
\set crew_count 10
\set user_count 50
-- ========================

BEGIN;

-- 기존 데이터 정리
DELETE FROM notifications WHERE target_id LIKE 'loadtest-sched-start-%';
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-sched-start-%';
DELETE FROM crews WHERE id LIKE 'loadtest-sched-start-%';

-- 크루 생성 (ACTIVE 상태, 오늘 시작)
INSERT INTO crews (id, creator_id, name, goal, verification_content, verification_type,
                   min_members, max_members, current_members, status,
                   start_date, end_date, allow_late_join, invite_code, created_at,
                   deadline_time, category, visibility)
SELECT
    'loadtest-sched-start-crew-' || LPAD(n::text, 4, '0'),
    'loadtest-user-' || ((n - 1) % :user_count + 1),
    '스케줄러테스트-시작알림-' || n,
    '시작 알림 테스트용 크루',
    '테스트 인증',
    'TEXT',
    1, 10, 5, 'ACTIVE',
    CURRENT_DATE,
    CURRENT_DATE + 6,
    true,
    'SS' || LPAD(n::text, 4, '0'),
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    '23:59:59',
    'ETC',
    'PUBLIC'
FROM generate_series(1, :crew_count) AS n;

-- 크루 멤버 (크루당 5명: LEADER 1 + MEMBER 4)
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-ss-' || LPAD(n::text, 4, '0') || '-' || m,
    'loadtest-user-' || (((n - 1) * 5 + m - 1) % :user_count + 1),
    'loadtest-sched-start-crew-' || LPAD(n::text, 4, '0'),
    CASE WHEN m = 1 THEN 'LEADER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM generate_series(1, :crew_count) AS n
CROSS JOIN generate_series(1, 5) AS m;

COMMIT;

-- 확인
SELECT count(*) AS active_crews
FROM crews WHERE id LIKE 'loadtest-sched-start-%' AND status = 'ACTIVE' AND start_date = CURRENT_DATE;
SELECT count(*) AS members
FROM crew_members WHERE crew_id LIKE 'loadtest-sched-start-%';
