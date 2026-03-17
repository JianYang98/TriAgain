-- =============================================
-- 3/15~16 챌린지 + 인증 테스트 데이터
-- =============================================
-- 목적: user 4777253873이 3/17에 인증하면 3일 연속 달성(SUCCESS)되도록
-- 크루: CREW-be0da6238a704698 (시작일 3/15, TEXT 인증)
-- =============================================

BEGIN;

-- 0. 크루 상태 확인 및 ACTIVE 전환 (startDate=3/15이므로 이미 ACTIVE여야 함)
UPDATE crews
SET status = 'ACTIVE'
WHERE id = 'CREW-be0da6238a704698'
  AND status != 'ACTIVE';

-- 1. 챌린지 1건 (IN_PROGRESS, completed_days=2)
INSERT INTO challenges (id, user_id, crew_id, cycle_number, target_days, completed_days, status, start_date, deadline, created_at)
VALUES (
    'CHAL-test031517abcdef',
    '4777253873',
    'CREW-be0da6238a704698',
    1,
    3,
    2,
    'IN_PROGRESS',
    '2026-03-15',
    '2026-03-18 23:59:59',
    '2026-03-15 09:00:00'
);

-- 2. 인증 — 3/15 (1건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0315abcdefgh',
    'CHAL-test031517abcdef',
    '4777253873',
    'CREW-be0da6238a704698',
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

-- 3. 인증 — 3/16 (1건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date, attempt_number,
                           review_status, created_at)
VALUES (
    'VRFY-test0316abcdefgh',
    'CHAL-test031517abcdef',
    '4777253873',
    'CREW-be0da6238a704698',
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
-- SELECT id, status, completed_days, start_date, deadline FROM challenges WHERE id = 'CHAL-test031517abcdef';
-- SELECT id, target_date, status, text_content FROM verifications WHERE challenge_id = 'CHAL-test031517abcdef' ORDER BY target_date;
-- SELECT id, status FROM crews WHERE id = 'CREW-be0da6238a704698';
