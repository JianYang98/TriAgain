-- =============================================
-- TriAgain 부하 테스트 시드 데이터
-- =============================================
-- 대상 API:
--   메인: GET /crews/{crewId}/feed (조회 부하, VUser 10→100→500)
--   서브: POST /verifications    (쓰기 부하 + 동시성, VUser 10→50)
--
-- 데이터 규모:
--   50 유저, 5 크루, 41 멤버, 80 챌린지, 120 인증, 10 업로드 세션
--
-- 날짜 기준: CURRENT_DATE (실행 시점 무관)
-- =============================================

BEGIN;

-- =============================================
-- 0. 기존 부하 테스트 데이터 정리 (멱등성 보장)
-- =============================================
-- 외래키 참조 순서 고려: verifications → upload_session → challenges → crew_members → crews → users

DELETE FROM verifications WHERE user_id LIKE 'load-user-%';
DELETE FROM upload_session WHERE user_id LIKE 'load-user-%';
DELETE FROM challenges WHERE user_id LIKE 'load-user-%';
DELETE FROM crew_members WHERE crew_id LIKE 'CREW-load-%';
DELETE FROM crews WHERE id LIKE 'CREW-load-%';
DELETE FROM users WHERE id LIKE 'load-user-%';

-- =============================================
-- 1. 유저 50명 (load-user-001 ~ load-user-050)
-- =============================================
-- provider: KAKAO (test-login 호환)
-- JWT 발급: POST /auth/test-login {"userId":"load-user-001"}

INSERT INTO users (id, provider, email, nickname, profile_image_url, created_at, terms_agreed_at)
SELECT
    'load-user-' || LPAD(n::text, 3, '0'),
    'KAKAO',
    'load' || LPAD(n::text, 3, '0') || '@test.com',
    'loaduser' || LPAD(n::text, 3, '0'),
    NULL,
    CURRENT_TIMESTAMP - INTERVAL '30 days',
    CURRENT_TIMESTAMP - INTERVAL '30 days'
FROM generate_series(1, 50) AS n;

-- =============================================
-- 2. 크루 5개 (TEXT 3 + PHOTO 1 + 빈 크루 1)
-- =============================================
-- 크루 1~3: TEXT 인증, 10명 만석, ACTIVE
-- 크루 4:   PHOTO 인증, 10명 만석, ACTIVE
-- 크루 5:   빈 크루 (리더만), 조인 테스트용

INSERT INTO crews (id, creator_id, name, goal, verification_content, verification_type,
                   min_members, max_members, current_members, status,
                   start_date, end_date, allow_late_join, invite_code, created_at, deadline_time)
VALUES
    ('CREW-load-text-1',  'load-user-001', '매일 운동 크루',   '매일 30분 운동하기',   '운동 인증', 'TEXT',
     1, 10, 10, 'ACTIVE', CURRENT_DATE - 14, CURRENT_DATE + 14, true, 'LDT001',
     CURRENT_TIMESTAMP - INTERVAL '14 days', '23:59:59'),

    ('CREW-load-text-2',  'load-user-011', '독서 크루',       '매일 30분 독서하기',   '독서 인증', 'TEXT',
     1, 10, 10, 'ACTIVE', CURRENT_DATE - 14, CURRENT_DATE + 14, true, 'LDT002',
     CURRENT_TIMESTAMP - INTERVAL '14 days', '23:59:59'),

    ('CREW-load-text-3',  'load-user-021', '명상 크루',       '매일 10분 명상하기',   '명상 인증', 'TEXT',
     1, 10, 10, 'ACTIVE', CURRENT_DATE - 14, CURRENT_DATE + 14, true, 'LDT003',
     CURRENT_TIMESTAMP - INTERVAL '14 days', '23:59:59'),

    ('CREW-load-photo-1', 'load-user-031', '식단 관리 크루',   '건강한 식단 인증하기', '식단 사진', 'PHOTO',
     1, 10, 10, 'ACTIVE', CURRENT_DATE - 14, CURRENT_DATE + 14, true, 'LDP001',
     CURRENT_TIMESTAMP - INTERVAL '14 days', '23:59:59'),

    ('CREW-load-empty-1', 'load-user-041', '조인 테스트 크루', '크루 참여 부하 테스트', '참여 인증', 'TEXT',
     1, 10, 1,  'ACTIVE', CURRENT_DATE - 14, CURRENT_DATE + 14, true, 'LDE001',
     CURRENT_TIMESTAMP - INTERVAL '14 days', '23:59:59');

-- =============================================
-- 3. 크루 멤버 41명 (4개 크루 × 10명 + 빈 크루 리더 1명)
-- =============================================

-- TEXT 크루 1: load-user-001 ~ 010
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-lt1-' || LPAD(n::text, 3, '0'),
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-1',
    CASE WHEN n = 1 THEN 'LEADER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - INTERVAL '14 days'
FROM generate_series(1, 10) AS n;

-- TEXT 크루 2: load-user-011 ~ 020
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-lt2-' || LPAD((n - 10)::text, 3, '0'),
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-2',
    CASE WHEN n = 11 THEN 'LEADER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - INTERVAL '14 days'
FROM generate_series(11, 20) AS n;

-- TEXT 크루 3: load-user-021 ~ 030
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-lt3-' || LPAD((n - 20)::text, 3, '0'),
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-3',
    CASE WHEN n = 21 THEN 'LEADER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - INTERVAL '14 days'
FROM generate_series(21, 30) AS n;

-- PHOTO 크루: load-user-031 ~ 040
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
SELECT
    'CRMB-lp1-' || LPAD((n - 30)::text, 3, '0'),
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-photo-1',
    CASE WHEN n = 31 THEN 'LEADER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - INTERVAL '14 days'
FROM generate_series(31, 40) AS n;

-- 빈 크루 리더: load-user-041
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
VALUES ('CRMB-le1-001', 'load-user-041', 'CREW-load-empty-1', 'LEADER',
        CURRENT_TIMESTAMP - INTERVAL '14 days');

-- =============================================
-- 4. 챌린지 80개
--    과거 SUCCESS 40개 (cycle 1, completed_days=3)
--    현재 IN_PROGRESS 40개 (cycle 2, completed_days=0)
-- =============================================

-- ---- 과거 SUCCESS 챌린지 (cycle 1) ----
-- 10일 전 시작, 8일 전 완료 (3일 사이클)

-- TEXT 크루 1
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lt1-' || LPAD(n::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-1',
    1, 3, 3, 'SUCCESS',
    CURRENT_DATE - 10,
    (CURRENT_DATE - 8) + TIME '23:59:59',
    CURRENT_TIMESTAMP - INTERVAL '10 days'
FROM generate_series(1, 10) AS n;

-- TEXT 크루 2
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lt2-' || LPAD((n - 10)::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-2',
    1, 3, 3, 'SUCCESS',
    CURRENT_DATE - 10,
    (CURRENT_DATE - 8) + TIME '23:59:59',
    CURRENT_TIMESTAMP - INTERVAL '10 days'
FROM generate_series(11, 20) AS n;

-- TEXT 크루 3
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lt3-' || LPAD((n - 20)::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-3',
    1, 3, 3, 'SUCCESS',
    CURRENT_DATE - 10,
    (CURRENT_DATE - 8) + TIME '23:59:59',
    CURRENT_TIMESTAMP - INTERVAL '10 days'
FROM generate_series(21, 30) AS n;

-- PHOTO 크루
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lp1-' || LPAD((n - 30)::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-photo-1',
    1, 3, 3, 'SUCCESS',
    CURRENT_DATE - 10,
    (CURRENT_DATE - 8) + TIME '23:59:59',
    CURRENT_TIMESTAMP - INTERVAL '10 days'
FROM generate_series(31, 40) AS n;

-- ---- 현재 IN_PROGRESS 챌린지 (cycle 2) ----
-- 오늘 시작, completed_days=0 → POST /verifications 테스트용

-- TEXT 크루 1
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lt1-' || LPAD(n::text, 3, '0') || '-c2',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-1',
    2, 3, 0, 'IN_PROGRESS',
    CURRENT_DATE,
    CURRENT_DATE + TIME '23:59:59',
    CURRENT_TIMESTAMP
FROM generate_series(1, 10) AS n;

-- TEXT 크루 2
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lt2-' || LPAD((n - 10)::text, 3, '0') || '-c2',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-2',
    2, 3, 0, 'IN_PROGRESS',
    CURRENT_DATE,
    CURRENT_DATE + TIME '23:59:59',
    CURRENT_TIMESTAMP
FROM generate_series(11, 20) AS n;

-- TEXT 크루 3
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lt3-' || LPAD((n - 20)::text, 3, '0') || '-c2',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-3',
    2, 3, 0, 'IN_PROGRESS',
    CURRENT_DATE,
    CURRENT_DATE + TIME '23:59:59',
    CURRENT_TIMESTAMP
FROM generate_series(21, 30) AS n;

-- PHOTO 크루
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
SELECT
    'CHAL-lp1-' || LPAD((n - 30)::text, 3, '0') || '-c2',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-photo-1',
    2, 3, 0, 'IN_PROGRESS',
    CURRENT_DATE,
    CURRENT_DATE + TIME '23:59:59',
    CURRENT_TIMESTAMP
FROM generate_series(31, 40) AS n;

-- =============================================
-- 5. 인증 120건 (과거 SUCCESS 챌린지의 3일치)
--    크루당 10명 × 3일 = 30건, 4개 크루 × 30 = 120건
-- =============================================
-- 날짜: CURRENT_DATE-10, -9, -8 (과거 사이클 3일)
-- 상태: APPROVED (피드에 노출)
-- 시간: 21:30, 22:00, 22:30 (마감 직전 패턴)

-- TEXT 크루 1 (30건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
SELECT
    'VRFY-lt1-' || LPAD(n::text, 3, '0') || '-d' || d,
    'CHAL-lt1-' || LPAD(n::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-1',
    NULL,
    NULL,
    '오늘도 운동 완료! Day ' || d || ' - user ' || LPAD(n::text, 3, '0'),
    'APPROVED', 0,
    CURRENT_DATE - (11 - d),
    d,
    'NOT_REQUIRED',
    (CURRENT_DATE - (11 - d))::timestamp + INTERVAL '21 hours' + (d * INTERVAL '30 minutes')
FROM generate_series(1, 10) AS n
CROSS JOIN generate_series(1, 3) AS d;

-- TEXT 크루 2 (30건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
SELECT
    'VRFY-lt2-' || LPAD((n - 10)::text, 3, '0') || '-d' || d,
    'CHAL-lt2-' || LPAD((n - 10)::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-2',
    NULL,
    NULL,
    '오늘도 독서 완료! Day ' || d || ' - user ' || LPAD(n::text, 3, '0'),
    'APPROVED', 0,
    CURRENT_DATE - (11 - d),
    d,
    'NOT_REQUIRED',
    (CURRENT_DATE - (11 - d))::timestamp + INTERVAL '21 hours' + (d * INTERVAL '30 minutes')
FROM generate_series(11, 20) AS n
CROSS JOIN generate_series(1, 3) AS d;

-- TEXT 크루 3 (30건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
SELECT
    'VRFY-lt3-' || LPAD((n - 20)::text, 3, '0') || '-d' || d,
    'CHAL-lt3-' || LPAD((n - 20)::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-text-3',
    NULL,
    NULL,
    '오늘도 명상 완료! Day ' || d || ' - user ' || LPAD(n::text, 3, '0'),
    'APPROVED', 0,
    CURRENT_DATE - (11 - d),
    d,
    'NOT_REQUIRED',
    (CURRENT_DATE - (11 - d))::timestamp + INTERVAL '21 hours' + (d * INTERVAL '30 minutes')
FROM generate_series(21, 30) AS n
CROSS JOIN generate_series(1, 3) AS d;

-- PHOTO 크루 (30건) — image_url 포함
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
SELECT
    'VRFY-lp1-' || LPAD((n - 30)::text, 3, '0') || '-d' || d,
    'CHAL-lp1-' || LPAD((n - 30)::text, 3, '0') || '-c1',
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-photo-1',
    NULL,
    'https://triagain-test.s3.ap-northeast-2.amazonaws.com/load-test/user-' || LPAD(n::text, 3, '0') || '/day' || d || '.jpg',
    '식단 인증 Day ' || d,
    'APPROVED', 0,
    CURRENT_DATE - (11 - d),
    d,
    'NOT_REQUIRED',
    (CURRENT_DATE - (11 - d))::timestamp + INTERVAL '21 hours' + (d * INTERVAL '30 minutes')
FROM generate_series(31, 40) AS n
CROSS JOIN generate_series(1, 3) AS d;

-- =============================================
-- 6. 업로드 세션 10건 (PHOTO 크루, COMPLETED)
-- =============================================
-- POST /verifications 사진 인증 테스트용
-- 각 세션은 아직 verification에 연결되지 않은 상태

INSERT INTO upload_session (id, user_id, crew_id, image_key, content_type, status, requested_at, created_at)
OVERRIDING SYSTEM VALUE
SELECT
    10000 + (n - 30),
    'load-user-' || LPAD(n::text, 3, '0'),
    'CREW-load-photo-1',
    'load-test/load-user-' || LPAD(n::text, 3, '0') || '/' || CURRENT_DATE || '/current.jpg',
    'image/jpeg',
    'COMPLETED',
    CURRENT_TIMESTAMP - INTERVAL '5 minutes',
    CURRENT_TIMESTAMP - INTERVAL '5 minutes'
FROM generate_series(31, 40) AS n;

COMMIT;

-- =============================================
-- 검증 쿼리 (실행 후 확인용)
-- =============================================
-- SELECT COUNT(*) AS user_count FROM users WHERE id LIKE 'load-user-%';               -- 50
-- SELECT COUNT(*) AS crew_count FROM crews WHERE id LIKE 'CREW-load-%';               -- 5
-- SELECT COUNT(*) AS member_count FROM crew_members WHERE crew_id LIKE 'CREW-load-%'; -- 41
-- SELECT COUNT(*) AS challenge_count FROM challenges WHERE user_id LIKE 'load-user-%';-- 80
-- SELECT COUNT(*) AS vrfy_count FROM verifications WHERE user_id LIKE 'load-user-%';  -- 120
-- SELECT COUNT(*) AS session_count FROM upload_session WHERE user_id LIKE 'load-user-%'; -- 10
