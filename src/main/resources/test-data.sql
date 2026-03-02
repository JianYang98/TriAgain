-- =============================================================
-- 테스트 데이터: 인증 현황 확인용
-- 기준일: 2026-03-01
-- 시나리오: 4명의 멤버가 각기 다른 인증 상태를 가진 크루
-- =============================================================

-- 기존 테스트 데이터 정리 (역순 삭제)
DELETE FROM verifications WHERE crew_id = 'CREW-test-001';
DELETE FROM challenges WHERE crew_id = 'CREW-test-001';
DELETE FROM crew_members WHERE crew_id = 'CREW-test-001';
DELETE FROM crews WHERE id = 'CREW-test-001';
DELETE FROM users WHERE id IN ('test-user-1', 'test-user-2', 'test-user-3', 'test-user-4');

-- -------------------------------------------------------------
-- 1. Users (4명)
-- -------------------------------------------------------------
INSERT INTO users (id, provider, email, nickname, profile_image_url, created_at) VALUES
('test-user-1', 'KAKAO', 'test1@triagain.com', '테스트유저1', NULL, '2026-02-20 10:00:00'),
('test-user-2', 'KAKAO', 'test2@triagain.com', '테스트유저2', NULL, '2026-02-20 10:00:00'),
('test-user-3', 'KAKAO', 'test3@triagain.com', '테스트유저3', NULL, '2026-02-20 10:00:00'),
('test-user-4', 'KAKAO', 'test4@triagain.com', '테스트유저4', NULL, '2026-02-20 10:00:00');

-- -------------------------------------------------------------
-- 2. Crew (1개, ACTIVE, TEXT 인증)
-- -------------------------------------------------------------
INSERT INTO crews (id, creator_id, name, goal, verification_type,
                   min_members, max_members, current_members,
                   status, start_date, end_date, allow_late_join,
                   invite_code, deadline_time, created_at) VALUES
('CREW-test-001', 'test-user-1', '작심삼일 테스트 크루', '매일 30분 독서하기', 'TEXT',
 1, 10, 4,
 'ACTIVE', '2026-02-27', '2026-03-10', false,
 'TST001', '23:59:59', '2026-02-20 10:00:00');

-- -------------------------------------------------------------
-- 3. Crew Members (4명: 크루장 1 + 멤버 3)
-- -------------------------------------------------------------
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at) VALUES
('CM-test-001', 'test-user-1', 'CREW-test-001', 'LEADER', '2026-02-20 10:00:00'),
('CM-test-002', 'test-user-2', 'CREW-test-001', 'MEMBER', '2026-02-21 10:00:00'),
('CM-test-003', 'test-user-3', 'CREW-test-001', 'MEMBER', '2026-02-22 10:00:00'),
('CM-test-004', 'test-user-4', 'CREW-test-001', 'MEMBER', '2026-02-23 10:00:00');

-- -------------------------------------------------------------
-- 4. Challenges (멤버별 1개, cycle 1, 시작 2/27 ~ 마감 3/2)
--    target_days=3, completed_days는 멤버별 다름
-- -------------------------------------------------------------
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
-- test-user-1: 1일 인증, IN_PROGRESS
('CH-test-001', 'test-user-1', 'CREW-test-001', 1, 3, 1, 'IN_PROGRESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00'),
-- test-user-2: 3일 모두 인증, SUCCESS
('CH-test-002', 'test-user-2', 'CREW-test-001', 1, 3, 3, 'SUCCESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00'),
-- test-user-3: 미인증, IN_PROGRESS
('CH-test-003', 'test-user-3', 'CREW-test-001', 1, 3, 0, 'IN_PROGRESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00'),
-- test-user-4: 2일 인증, IN_PROGRESS
('CH-test-004', 'test-user-4', 'CREW-test-001', 1, 3, 2, 'IN_PROGRESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00');

-- -------------------------------------------------------------
-- 5. Verifications (총 6건)
--    TEXT 인증 → image_url/upload_session_id 없음
--    status=APPROVED, review_status=NOT_REQUIRED
-- -------------------------------------------------------------

-- test-user-1: Day1(2/27)만 인증
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-test-001', 'CH-test-001', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '오늘 독서 30분 완료! 재밌는 소설이라 시간이 금방 갔다.',
 'APPROVED', 0, '2026-02-27', 1, 'NOT_REQUIRED', '2026-02-27 21:00:00');

-- test-user-2: Day1(2/27), Day2(2/28), Day3(3/1) 모두 인증 → 작심삼일 성공!
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-test-002', 'CH-test-002', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '1일차 독서 완료. 자기계발서 읽기 시작!',
 'APPROVED', 0, '2026-02-27', 1, 'NOT_REQUIRED', '2026-02-27 20:00:00'),
('VF-test-003', 'CH-test-002', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '2일차 독서 완료. 점점 재밌어진다.',
 'APPROVED', 0, '2026-02-28', 1, 'NOT_REQUIRED', '2026-02-28 19:30:00'),
('VF-test-004', 'CH-test-002', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '3일차 독서 완료! 작심삼일 성공했다!',
 'APPROVED', 0, '2026-03-01', 1, 'NOT_REQUIRED', '2026-03-01 18:00:00');

-- test-user-3: 미인증 (verifications 없음)

-- test-user-4: Day1(2/27), Day2(2/28) 인증
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-test-005', 'CH-test-004', 'test-user-4', 'CREW-test-001',
 NULL, NULL, '독서 시작! 오늘은 에세이를 읽었다.',
 'APPROVED', 0, '2026-02-27', 1, 'NOT_REQUIRED', '2026-02-27 22:00:00'),
('VF-test-006', 'CH-test-004', 'test-user-4', 'CREW-test-001',
 NULL, NULL, '2일째 독서 완료. 내일도 할 수 있을까?',
 'APPROVED', 0, '2026-02-28', 1, 'NOT_REQUIRED', '2026-02-28 21:30:00');

-- =============================================================
-- 검증 쿼리
-- =============================================================
-- SELECT * FROM verifications WHERE crew_id = 'CREW-test-001';
--   → 6건
--
-- SELECT * FROM challenges WHERE crew_id = 'CREW-test-001';
--   → 4건 (IN_PROGRESS 3건 + SUCCESS 1건)
--
-- SELECT u.nickname, c.completed_days, c.status
-- FROM challenges c JOIN users u ON c.user_id = u.id
-- WHERE c.crew_id = 'CREW-test-001';
--   → 테스트유저1: 1, IN_PROGRESS
--   → 테스트유저2: 3, SUCCESS
--   → 테스트유저3: 0, IN_PROGRESS
--   → 테스트유저4: 2, IN_PROGRESS
