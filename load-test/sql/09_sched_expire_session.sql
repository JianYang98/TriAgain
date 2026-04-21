-- =============================================================
-- 09_sched_expire_session.sql — ExpireUploadSessionScheduler
-- =============================================================
-- 타겟 조건: upload_session.status = 'PENDING' AND created_at < NOW() - 15min
-- 스케줄러 동작: PENDING → EXPIRED 전환
-- 선행: 01_users.sql 실행 완료
-- =============================================================

-- ===== SCALE CONFIG =====
-- S=50, M=250, L=1000, XL=2500
\set session_count 50
\set user_count 50
-- ========================

BEGIN;

-- 기존 데이터 정리
DELETE FROM upload_session WHERE image_key LIKE 'loadtest-sched-upload/%';

-- 업로드 세션 생성 (PENDING 상태, 15분 임계 초과)
INSERT INTO upload_session (id, user_id, crew_id, image_key, content_type,
                            status, requested_at, created_at)
OVERRIDING SYSTEM VALUE
SELECT
    20000 + n,
    'loadtest-user-' || ((n - 1) % :user_count + 1),
    NULL,
    'loadtest-sched-upload/' || LPAD(n::text, 5, '0') || '.jpg',
    'image/jpeg',
    'PENDING',
    NOW() - INTERVAL '30 minutes',
    NOW() - INTERVAL '30 minutes'
FROM generate_series(1, :session_count) AS n;

COMMIT;

-- 확인
SELECT count(*) AS pending_sessions
FROM upload_session WHERE image_key LIKE 'loadtest-sched-upload/%' AND status = 'PENDING';
