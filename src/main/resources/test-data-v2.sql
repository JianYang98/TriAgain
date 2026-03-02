-- =============================================================
-- 테스트 데이터 v2: 풍성한 인증 현황 확인용
-- 기준일: 2026-03-02
-- =============================================================
-- 시나리오 요약:
--   크루1 "매일 독서 크루" (ACTIVE, 중간가입 허용, 5명)
--     - test-user-1: 작심삼일 2회 달성 + 현재 3번째 도전 2/3 진행중
--     - test-user-2: 작심삼일 1회 달성 + 현재 2번째 도전 3/3 완료
--     - test-user-3: 작심삼일 0회, 현재 1번째 도전 1/3
--     - test-user-4: 작심삼일 1회 달성 + 현재 2번째 도전 0/3 (미인증)
--     - test-user-5: 중간가입, 현재 1번째 도전 2/3
--
--   크루2 "매일 운동 크루" (ACTIVE, 중간가입 불가, 3명)
--     - test-user-1: 현재 1번째 도전 1/3
--     - test-user-6: 현재 1번째 도전 3/3 완료
--     - test-user-7: 현재 1번째 도전 0/3
-- =============================================================

-- 기존 테스트 데이터 정리 (역순 삭제)
DELETE FROM verifications WHERE crew_id IN ('CREW-test-001', 'CREW-test-002');
DELETE FROM challenges WHERE crew_id IN ('CREW-test-001', 'CREW-test-002');
DELETE FROM crew_members WHERE crew_id IN ('CREW-test-001', 'CREW-test-002');
DELETE FROM crews WHERE id IN ('CREW-test-001', 'CREW-test-002');
DELETE FROM users WHERE id IN ('test-user-1', 'test-user-2', 'test-user-3', 'test-user-4', 'test-user-5', 'test-user-6', 'test-user-7');


-- =============================================================
-- 1. Users (7명, provider=LOCAL)
-- =============================================================
INSERT INTO users (id, provider, email, nickname, profile_image_url, created_at) VALUES
('test-user-1', 'LOCAL', 'user1@test.com',  '지안',     NULL, '2026-02-10 10:00:00'),
('test-user-2', 'LOCAL', 'user2@test.com',  '민수',     NULL, '2026-02-10 10:00:00'),
('test-user-3', 'LOCAL', 'user3@test.com',  '수진',     NULL, '2026-02-11 10:00:00'),
('test-user-4', 'LOCAL', 'user4@test.com',  '현우',     NULL, '2026-02-11 10:00:00'),
('test-user-5', 'LOCAL', 'user5@test.com',  '예린',     NULL, '2026-02-20 10:00:00'),
('test-user-6', 'LOCAL', 'user6@test.com',  '준호',     NULL, '2026-02-12 10:00:00'),
('test-user-7', 'LOCAL', 'user7@test.com',  '소영',     NULL, '2026-02-12 10:00:00');


-- =============================================================
-- 2. Crews (2개)
-- =============================================================

-- 크루1: 매일 독서 크루 (중간가입 허용, 초대코드 READ26)
INSERT INTO crews (id, creator_id, name, goal, verification_type,
                   min_members, max_members, current_members,
                   status, start_date, end_date, allow_late_join,
                   invite_code, deadline_time, created_at) VALUES
('CREW-test-001', 'test-user-1',
 '매일 독서 크루', '매일 30분 이상 독서하기', 'TEXT',
 1, 10, 5,
 'ACTIVE', '2026-02-15', '2026-03-15', true,
 'READ26', '23:59:59', '2026-02-10 10:00:00');

-- 크루2: 매일 운동 크루 (중간가입 불가, 초대코드 FIT026)
INSERT INTO crews (id, creator_id, name, goal, verification_type,
                   min_members, max_members, current_members,
                   status, start_date, end_date, allow_late_join,
                   invite_code, deadline_time, created_at) VALUES
('CREW-test-002', 'test-user-6',
 '매일 운동 크루', '매일 30분 운동하기', 'TEXT',
 1, 5, 3,
 'ACTIVE', '2026-02-25', '2026-03-15', false,
 'FIT026', '22:00:00', '2026-02-12 10:00:00');


-- =============================================================
-- 3. Crew Members
-- =============================================================

-- 크루1 멤버 (5명)
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at) VALUES
('CM-001', 'test-user-1', 'CREW-test-001', 'LEADER', '2026-02-10 10:00:00'),
('CM-002', 'test-user-2', 'CREW-test-001', 'MEMBER', '2026-02-11 10:00:00'),
('CM-003', 'test-user-3', 'CREW-test-001', 'MEMBER', '2026-02-12 10:00:00'),
('CM-004', 'test-user-4', 'CREW-test-001', 'MEMBER', '2026-02-13 10:00:00'),
('CM-005', 'test-user-5', 'CREW-test-001', 'MEMBER', '2026-02-22 10:00:00');

-- 크루2 멤버 (3명)
INSERT INTO crew_members (id, user_id, crew_id, role, joined_at) VALUES
('CM-006', 'test-user-1', 'CREW-test-002', 'MEMBER', '2026-02-13 10:00:00'),
('CM-007', 'test-user-6', 'CREW-test-002', 'LEADER', '2026-02-12 10:00:00'),
('CM-008', 'test-user-7', 'CREW-test-002', 'MEMBER', '2026-02-14 10:00:00');


-- =============================================================
-- 4. Challenges - 크루1 (매일 독서 크루)
-- =============================================================
-- 타임라인 (크루 시작 2/15):
--   cycle 1: 2/15 ~ 2/18 (마감 2/18 23:59:59)
--   cycle 2: 2/18 ~ 2/21 (마감 2/21 23:59:59)
--   cycle 3: 2/21 ~ 2/24 (마감 2/24 23:59:59)
--   cycle 4: 2/24 ~ 2/27 (마감 2/27 23:59:59)
--   cycle 5: 2/27 ~ 3/02 (마감 3/02 23:59:59) ← 현재 진행중
-- =============================================================

-- ─── test-user-1 (지안): 2회 달성 + 현재 3번째 도전 2/3 ───
-- cycle 1: SUCCESS (작심삼일 1회차 달성!)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-101', 'test-user-1', 'CREW-test-001', 1, 3, 3, 'SUCCESS',
 '2026-02-15', '2026-02-18 23:59:59', '2026-02-15 00:00:00');
-- cycle 2: FAILED
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-102', 'test-user-1', 'CREW-test-001', 2, 3, 1, 'FAILED',
 '2026-02-18', '2026-02-21 23:59:59', '2026-02-18 00:00:00');
-- cycle 3: SUCCESS (작심삼일 2회차 달성!)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-103', 'test-user-1', 'CREW-test-001', 3, 3, 3, 'SUCCESS',
 '2026-02-21', '2026-02-24 23:59:59', '2026-02-21 00:00:00');
-- cycle 4: FAILED
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-104', 'test-user-1', 'CREW-test-001', 4, 3, 2, 'FAILED',
 '2026-02-24', '2026-02-27 23:59:59', '2026-02-24 00:00:00');
-- cycle 5: IN_PROGRESS (현재 3번째 도전중, 2/3)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-105', 'test-user-1', 'CREW-test-001', 5, 3, 2, 'IN_PROGRESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00');

-- ─── test-user-2 (민수): 1회 달성 + 현재 2번째 도전 3/3 Done ───
-- cycle 1: FAILED
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-201', 'test-user-2', 'CREW-test-001', 1, 3, 0, 'FAILED',
 '2026-02-15', '2026-02-18 23:59:59', '2026-02-15 00:00:00');
-- cycle 2: SUCCESS (작심삼일 1회차!)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-202', 'test-user-2', 'CREW-test-001', 2, 3, 3, 'SUCCESS',
 '2026-02-18', '2026-02-21 23:59:59', '2026-02-18 00:00:00');
-- cycle 3: FAILED
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-203', 'test-user-2', 'CREW-test-001', 3, 3, 2, 'FAILED',
 '2026-02-21', '2026-02-24 23:59:59', '2026-02-21 00:00:00');
-- cycle 4: FAILED
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-204', 'test-user-2', 'CREW-test-001', 4, 3, 1, 'FAILED',
 '2026-02-24', '2026-02-27 23:59:59', '2026-02-24 00:00:00');
-- cycle 5: SUCCESS (작심삼일 2회차! Done!)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-205', 'test-user-2', 'CREW-test-001', 5, 3, 3, 'SUCCESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00');

-- ─── test-user-3 (수진): 0회 달성, 현재 1번째 도전 1/3 ───
-- cycle 1~4: 전부 FAILED (꾸준히 실패했지만 포기 안 함!)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-301', 'test-user-3', 'CREW-test-001', 1, 3, 1, 'FAILED',
 '2026-02-15', '2026-02-18 23:59:59', '2026-02-15 00:00:00'),
('CH-302', 'test-user-3', 'CREW-test-001', 2, 3, 2, 'FAILED',
 '2026-02-18', '2026-02-21 23:59:59', '2026-02-18 00:00:00'),
('CH-303', 'test-user-3', 'CREW-test-001', 3, 3, 0, 'FAILED',
 '2026-02-21', '2026-02-24 23:59:59', '2026-02-21 00:00:00'),
('CH-304', 'test-user-3', 'CREW-test-001', 4, 3, 2, 'FAILED',
 '2026-02-24', '2026-02-27 23:59:59', '2026-02-24 00:00:00');
-- cycle 5: IN_PROGRESS (1/3)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-305', 'test-user-3', 'CREW-test-001', 5, 3, 1, 'IN_PROGRESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00');

-- ─── test-user-4 (현우): 1회 달성 + 현재 0/3 (미인증) ───
-- cycle 1: SUCCESS (작심삼일 1회차!)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-401', 'test-user-4', 'CREW-test-001', 1, 3, 3, 'SUCCESS',
 '2026-02-15', '2026-02-18 23:59:59', '2026-02-15 00:00:00');
-- cycle 2~4: FAILED
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-402', 'test-user-4', 'CREW-test-001', 2, 3, 1, 'FAILED',
 '2026-02-18', '2026-02-21 23:59:59', '2026-02-18 00:00:00'),
('CH-403', 'test-user-4', 'CREW-test-001', 3, 3, 0, 'FAILED',
 '2026-02-21', '2026-02-24 23:59:59', '2026-02-21 00:00:00'),
('CH-404', 'test-user-4', 'CREW-test-001', 4, 3, 2, 'FAILED',
 '2026-02-24', '2026-02-27 23:59:59', '2026-02-24 00:00:00');
-- cycle 5: IN_PROGRESS (0/3 미인증)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-405', 'test-user-4', 'CREW-test-001', 5, 3, 0, 'IN_PROGRESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00');

-- ─── test-user-5 (예린): 중간가입, 현재 1번째 도전 2/3 ───
-- 2/22 가입이므로 cycle 4부터 참여
-- cycle 4: FAILED
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-501', 'test-user-5', 'CREW-test-001', 4, 3, 1, 'FAILED',
 '2026-02-24', '2026-02-27 23:59:59', '2026-02-24 00:00:00');
-- cycle 5: IN_PROGRESS (2/3)
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-502', 'test-user-5', 'CREW-test-001', 5, 3, 2, 'IN_PROGRESS',
 '2026-02-27', '2026-03-02 23:59:59', '2026-02-27 00:00:00');


-- =============================================================
-- 5. Challenges - 크루2 (매일 운동 크루)
-- =============================================================
-- 타임라인 (크루 시작 2/25):
--   cycle 1: 2/25 ~ 2/28 (마감 2/28 22:00:00) ← 마감시간 22시!
--   cycle 2: 2/28 ~ 3/03 (마감 3/03 22:00:00) ← 현재 진행중

-- ─── test-user-1 (지안): 현재 1번째 도전 1/3 ───
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-601', 'test-user-1', 'CREW-test-002', 1, 3, 2, 'FAILED',
 '2026-02-25', '2026-02-28 22:00:00', '2026-02-25 00:00:00'),
('CH-602', 'test-user-1', 'CREW-test-002', 2, 3, 1, 'IN_PROGRESS',
 '2026-02-28', '2026-03-03 22:00:00', '2026-02-28 00:00:00');

-- ─── test-user-6 (준호): 1회 달성 + 현재 3/3 Done ───
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-701', 'test-user-6', 'CREW-test-002', 1, 3, 3, 'SUCCESS',
 '2026-02-25', '2026-02-28 22:00:00', '2026-02-25 00:00:00'),
('CH-702', 'test-user-6', 'CREW-test-002', 2, 3, 3, 'SUCCESS',
 '2026-02-28', '2026-03-03 22:00:00', '2026-02-28 00:00:00');

-- ─── test-user-7 (소영): 현재 0/3 미인증 ───
INSERT INTO challenges (id, user_id, crew_id, cycle_number,
                        target_days, completed_days, status,
                        start_date, deadline, created_at) VALUES
('CH-801', 'test-user-7', 'CREW-test-002', 1, 3, 1, 'FAILED',
 '2026-02-25', '2026-02-28 22:00:00', '2026-02-25 00:00:00'),
('CH-802', 'test-user-7', 'CREW-test-002', 2, 3, 0, 'IN_PROGRESS',
 '2026-02-28', '2026-03-03 22:00:00', '2026-02-28 00:00:00');


-- =============================================================
-- 6. Verifications - 크루1 현재 사이클 (cycle 5: 2/27~3/02)
-- =============================================================

-- test-user-1 (지안): 2/3 → Day1, Day2 인증
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-1051', 'CH-105', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '오늘 독서 30분 완료! 소설 너무 재밌다.',
 'APPROVED', 0, '2026-02-27', 1, 'NOT_REQUIRED', '2026-02-27 21:00:00'),
('VF-1052', 'CH-105', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '2일차! 에세이 읽었다. 내일이면 3번째 작심삼일!',
 'APPROVED', 0, '2026-02-28', 1, 'NOT_REQUIRED', '2026-02-28 22:30:00');

-- test-user-2 (민수): 3/3 Done → Day1, Day2, Day3 인증
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-2051', 'CH-205', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '자기계발서 1장 읽기 완료!',
 'APPROVED', 0, '2026-02-27', 1, 'NOT_REQUIRED', '2026-02-27 20:00:00'),
('VF-2052', 'CH-205', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '2일차 독서 완료. 이번엔 꼭 해내자.',
 'APPROVED', 0, '2026-02-28', 1, 'NOT_REQUIRED', '2026-02-28 19:30:00'),
('VF-2053', 'CH-205', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '3일차 완료!! 드디어 2번째 작심삼일 달성!!',
 'APPROVED', 0, '2026-03-01', 1, 'NOT_REQUIRED', '2026-03-01 18:00:00');

-- test-user-3 (수진): 1/3 → Day1만 인증
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-3051', 'CH-305', 'test-user-3', 'CREW-test-001',
 NULL, NULL, '오늘은 해냈다! 짧은 시 한 편 읽었어요.',
 'APPROVED', 0, '2026-02-27', 1, 'NOT_REQUIRED', '2026-02-27 23:50:00');

-- test-user-4 (현우): 0/3 → 인증 없음

-- test-user-5 (예린): 2/3 → Day1, Day2 인증
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-5021', 'CH-502', 'test-user-5', 'CREW-test-001',
 NULL, NULL, '첫 인증! 짧은 에세이 읽었어요~',
 'APPROVED', 0, '2026-02-27', 1, 'NOT_REQUIRED', '2026-02-27 20:30:00'),
('VF-5022', 'CH-502', 'test-user-5', 'CREW-test-001',
 NULL, NULL, '2일차 독서! 점점 습관이 되는 느낌.',
 'APPROVED', 0, '2026-02-28', 1, 'NOT_REQUIRED', '2026-02-28 21:00:00');


-- =============================================================
-- 7. Verifications - 크루1 과거 사이클 (달성 증거)
-- =============================================================

-- test-user-1 cycle 1 SUCCESS 인증 (3건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-1011', 'CH-101', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '첫 인증! 독서 시작합니다.',
 'APPROVED', 0, '2026-02-15', 1, 'NOT_REQUIRED', '2026-02-15 21:00:00'),
('VF-1012', 'CH-101', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '2일차 독서. 재밌는 소설 발견!',
 'APPROVED', 0, '2026-02-16', 1, 'NOT_REQUIRED', '2026-02-16 22:00:00'),
('VF-1013', 'CH-101', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '3일차 완료! 첫 작심삼일 달성!',
 'APPROVED', 0, '2026-02-17', 1, 'NOT_REQUIRED', '2026-02-17 20:00:00');

-- test-user-1 cycle 3 SUCCESS 인증 (3건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-1031', 'CH-103', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '다시 도전! 오늘도 독서 완료.',
 'APPROVED', 0, '2026-02-21', 1, 'NOT_REQUIRED', '2026-02-21 21:00:00'),
('VF-1032', 'CH-103', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '2일째. 이번엔 해낸다.',
 'APPROVED', 0, '2026-02-22', 1, 'NOT_REQUIRED', '2026-02-22 21:30:00'),
('VF-1033', 'CH-103', 'test-user-1', 'CREW-test-001',
 NULL, NULL, '두 번째 작심삼일 달성!! 기분 좋다!',
 'APPROVED', 0, '2026-02-23', 1, 'NOT_REQUIRED', '2026-02-23 19:00:00');

-- test-user-2 cycle 2 SUCCESS 인증 (3건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-2021', 'CH-202', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '다시 시작! 오늘 자기계발서 읽었어요.',
 'APPROVED', 0, '2026-02-18', 1, 'NOT_REQUIRED', '2026-02-18 20:00:00'),
('VF-2022', 'CH-202', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '2일차! 포기하지 않겠다.',
 'APPROVED', 0, '2026-02-19', 1, 'NOT_REQUIRED', '2026-02-19 21:00:00'),
('VF-2023', 'CH-202', 'test-user-2', 'CREW-test-001',
 NULL, NULL, '작심삼일 성공!! 해냈다!!',
 'APPROVED', 0, '2026-02-20', 1, 'NOT_REQUIRED', '2026-02-20 19:00:00');

-- test-user-4 cycle 1 SUCCESS 인증 (3건)
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-4011', 'CH-401', 'test-user-4', 'CREW-test-001',
 NULL, NULL, '독서 도전! 경제 책 읽기 시작.',
 'APPROVED', 0, '2026-02-15', 1, 'NOT_REQUIRED', '2026-02-15 22:00:00'),
('VF-4012', 'CH-401', 'test-user-4', 'CREW-test-001',
 NULL, NULL, '2일차. 어렵지만 재밌다.',
 'APPROVED', 0, '2026-02-16', 1, 'NOT_REQUIRED', '2026-02-16 21:30:00'),
('VF-4013', 'CH-401', 'test-user-4', 'CREW-test-001',
 NULL, NULL, '3일 완료! 나도 해냈다!',
 'APPROVED', 0, '2026-02-17', 1, 'NOT_REQUIRED', '2026-02-17 20:30:00');


-- =============================================================
-- 8. Verifications - 크루2 현재 사이클 (cycle 2: 2/28~3/03)
-- =============================================================

-- test-user-1 (지안): 1/3
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-6021', 'CH-602', 'test-user-1', 'CREW-test-002',
 NULL, NULL, '운동 30분 완료! 홈트했다.',
 'APPROVED', 0, '2026-02-28', 1, 'NOT_REQUIRED', '2026-02-28 21:00:00');

-- test-user-6 (준호): 3/3 Done
INSERT INTO verifications (id, challenge_id, user_id, crew_id,
                           upload_session_id, image_url, text_content,
                           status, report_count, target_date,
                           attempt_number, review_status, created_at) VALUES
('VF-7021', 'CH-702', 'test-user-6', 'CREW-test-002',
 NULL, NULL, '오늘도 러닝 5km 완료!',
 'APPROVED', 0, '2026-02-28', 1, 'NOT_REQUIRED', '2026-02-28 19:00:00'),
('VF-7022', 'CH-702', 'test-user-6', 'CREW-test-002',
 NULL, NULL, '러닝 + 스트레칭. 몸 상태 최고.',
 'APPROVED', 0, '2026-03-01', 1, 'NOT_REQUIRED', '2026-03-01 18:30:00'),
('VF-7023', 'CH-702', 'test-user-6', 'CREW-test-002',
 NULL, NULL, '3일 완료! 운동 습관 잡히는 중!',
 'APPROVED', 0, '2026-03-02', 1, 'NOT_REQUIRED', '2026-03-02 07:00:00');

-- test-user-7 (소영): 0/3 미인증


-- =============================================================
-- 검증 쿼리
-- =============================================================
-- 크루1 참가자 현황 (현재 사이클 기준 + 누적 달성 횟수)
--
-- SELECT u.nickname,
--        (SELECT COUNT(*) FROM challenges c2
--         WHERE c2.user_id = u.id AND c2.crew_id = 'CREW-test-001'
--         AND c2.status = 'SUCCESS') AS total_success,
--        c.completed_days, c.target_days, c.status
-- FROM challenges c
-- JOIN users u ON c.user_id = u.id
-- WHERE c.crew_id = 'CREW-test-001'
--   AND c.cycle_number = 5
-- ORDER BY total_success DESC, c.completed_days DESC;
--
-- 기대 결과 (정렬: 달성횟수 → 진행률):
--   민수: 2회 달성, 3/3 Done
--   지안: 2회 달성, 2/3 (동률이지만 민수가 먼저 달성)
--   현우: 1회 달성, 0/3
--   수진: 0회 달성, 1/3
--   예린: 0회 달성, 2/3
