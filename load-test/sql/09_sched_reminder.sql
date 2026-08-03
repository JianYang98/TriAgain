-- =============================================================
-- 09_sched_reminder.sql — ReminderScheduler
-- =============================================================
-- 타겟 조건: ACTIVE crew + deadline_time이 now+15분~now+30분 + 오늘 미인증
-- 스케줄러 동작: 마감 임박 미인증 멤버에게 리마인더 알림 발송
-- 주의: deadline_time이 동적 — SQL 실행 후 30분 이내에 스케줄러 트리거해야 함
-- 선행: 01_users.sql 실행 완료
-- =============================================================

-- ===== SCALE CONFIG =====
-- S=10, M=50, L=200, XL=500  (멤버수 = crew_count × 5)
\set crew_count 10
\set user_count 50
-- ========================

BEGIN;

-- 기존 데이터 정리
DELETE FROM notifications WHERE target_id LIKE 'loadtest-sched-remind-%';
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-sched-remind-%';
DELETE FROM crews WHERE id LIKE 'loadtest-sched-remind-%';

-- 크루 생성 (ACTIVE 상태, deadline_time = 20분 후 → 15~30분 윈도우 안)
INSERT INTO crews (id, creator_id, name, goal, verification_content, verification_type,
                   min_members, max_members, current_members, status,
                   start_date, end_date, allow_late_join, invite_code, created_at,
                   deadline_time, category, visibility)
SELECT
    'loadtest-sched-remind-crew-' || LPAD(n::text, 4, '0'),
    'loadtest-user-' || ((n - 1) % :user_count + 1),
    '스케줄러테스트-리마인더-' || n,
    '리마인더 테스트용 크루',
    '테스트 인증',
    'TEXT',
    1, 10, 5, 'ACTIVE',
    CURRENT_DATE - 3,
    CURRENT_DATE + 3,
    true,
    'SR' || LPAD(n::text, 4, '0'),
    CURRENT_TIMESTAMP - INTERVAL '5 days',
    (NOW() + INTERVAL '20 minutes')::time,
    'ETC',
    'PUBLIC'
FROM generate_series(1, :crew_count) AS n;

-- 크루 멤버 (크루당 5명, verifications 없음 = 전원 미인증 = 리마인더 대상)
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-srem-' || LPAD(n::text, 4, '0') || '-' || m,
    'loadtest-user-' || (((n - 1) * 5 + m - 1) % :user_count + 1),
    'loadtest-sched-remind-crew-' || LPAD(n::text, 4, '0'),
    CASE WHEN m = 1 THEN 'LEADER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - INTERVAL '5 days'
FROM generate_series(1, :crew_count) AS n
CROSS JOIN generate_series(1, 5) AS m;

COMMIT;

-- 확인
SELECT count(*) AS active_crews
FROM crews WHERE id LIKE 'loadtest-sched-remind-%' AND status = 'ACTIVE';
SELECT count(*) AS members
FROM crew_members WHERE crew_id LIKE 'loadtest-sched-remind-%';
SELECT deadline_time, (NOW() + INTERVAL '20 minutes')::time AS expected
FROM crews WHERE id = 'loadtest-sched-remind-crew-0001';
