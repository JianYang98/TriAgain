-- =============================================
-- CREW-877435564443481b 챌린지 + 인증 테스트 데이터
-- =============================================
-- 목적: user 4777253873이 3/17에 인증하면 cycle 3이 3일 연속 달성(SUCCESS)되도록
-- 크루: CREW-877435564443481b (시작일 3/8, TEXT 인증)
-- 사이클 설계:
--   Cycle 1 (3/8~3/10): SUCCESS  — 3/8, 3/9, 3/10 인증
--   Cycle 2 (3/11~3/13): FAILED  — 3/12만 인증
--   Cycle 3 (3/15~3/17): IN_PROGRESS — 3/15, 3/16 인증, 3/17 인증 시 SUCCESS
-- =============================================

BEGIN;

-- 0. 크루 상태 ACTIVE 전환
UPDATE crews
SET status = 'ACTIVE'
WHERE id = 'CREW-877435564443481b'
  AND status != 'ACTIVE';

-- 1. 챌린지 — Cycle 1 (SUCCESS)
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
VALUES (
    'CHAL-test0308c1abcdef',
    '4777253873',
    'CREW-877435564443481b',
    1,
    3,
    3,
    'SUCCESS',
    '2026-03-08',
    '2026-03-11 23:59:59',
    '2026-03-08 09:00:00'
);

-- 2. 챌린지 — Cycle 2 (FAILED)
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
VALUES (
    'CHAL-test0308c2abcdef',
    '4777253873',
    'CREW-877435564443481b',
    2,
    3,
    1,
    'FAILED',
    '2026-03-11',
    '2026-03-14 23:59:59',
    '2026-03-11 09:00:00'
);

-- 3. 챌린지 — Cycle 3 (IN_PROGRESS, 3/17 인증 시 SUCCESS)
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
VALUES (
    'CHAL-test0308c3abcdef',
    '4777253873',
    'CREW-877435564443481b',
    3,
    3,
    2,
    'IN_PROGRESS',
    '2026-03-15',
    '2026-03-18 23:59:59',
    '2026-03-15 09:00:00'
);

-- 4. 인증 — Cycle 1: 3/8
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0308d1-0308',
    'CHAL-test0308c1abcdef',
    '4777253873',
    'CREW-877435564443481b',
    NULL,
    NULL,
    '3월 8일 인증합니다',
    'APPROVED',
    0,
    '2026-03-08',
    1,
    'NOT_REQUIRED',
    '2026-03-08 10:00:00'
);

-- 5. 인증 — Cycle 1: 3/9
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0308d2-0309',
    'CHAL-test0308c1abcdef',
    '4777253873',
    'CREW-877435564443481b',
    NULL,
    NULL,
    '3월 9일 인증합니다',
    'APPROVED',
    0,
    '2026-03-09',
    1,
    'NOT_REQUIRED',
    '2026-03-09 10:00:00'
);

-- 6. 인증 — Cycle 1: 3/10
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0308d3-0310',
    'CHAL-test0308c1abcdef',
    '4777253873',
    'CREW-877435564443481b',
    NULL,
    NULL,
    '3월 10일 인증합니다',
    'APPROVED',
    0,
    '2026-03-10',
    1,
    'NOT_REQUIRED',
    '2026-03-10 10:00:00'
);

-- 7. 인증 — Cycle 2: 3/12
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0308d1-0312',
    'CHAL-test0308c2abcdef',
    '4777253873',
    'CREW-877435564443481b',
    NULL,
    NULL,
    '3월 12일 인증합니다',
    'APPROVED',
    0,
    '2026-03-12',
    1,
    'NOT_REQUIRED',
    '2026-03-12 10:00:00'
);

-- 8. 인증 — Cycle 3: 3/15
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0308d1-0315',
    'CHAL-test0308c3abcdef',
    '4777253873',
    'CREW-877435564443481b',
    NULL,
    NULL,
    '3월 15일 인증합니다',
    'APPROVED',
    0,
    '2026-03-15',
    1,
    'NOT_REQUIRED',
    '2026-03-15 10:00:00'
);

-- 9. 인증 — Cycle 3: 3/16
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0308d2-0316',
    'CHAL-test0308c3abcdef',
    '4777253873',
    'CREW-877435564443481b',
    NULL,
    NULL,
    '3월 16일 인증합니다',
    'APPROVED',
    0,
    '2026-03-16',
    1,
    'NOT_REQUIRED',
    '2026-03-16 10:00:00'
);

COMMIT;

-- =============================================
-- 검증 쿼리 (실행 후 확인용)
-- =============================================
-- SELECT id, cycle_number, status, completed_days, start_date, deadline FROM challenges WHERE crew_id = 'CREW-877435564443481b' ORDER BY cycle_number;
-- SELECT id, challenge_id, target_date, status, text_content FROM verifications WHERE crew_id = 'CREW-877435564443481b' ORDER BY target_date;
-- SELECT id, status FROM crews WHERE id = 'CREW-877435564443481b';
