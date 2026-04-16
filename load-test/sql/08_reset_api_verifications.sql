-- ============================================================
-- API 인증 리셋 (반복 실행용)
-- 테스트 사이에 실행하여 verify_duplicate(409)를 제거하고
--  챌린지를 미인증 초기 상태로 복구
-- 대상: 04_challenges_api.sql이 만든 loadtest-crew-* + loadtest-user-*
-- 비대상: loadtest-rush-crew-* (07_rush_reset.sql 사용)
--        loadtest-sched-crew-* (스케줄러 테스트 데이터)
-- ============================================================

-- 1. 오늘자 loadtest 인증 삭제 → UNIQUE(user_id, crew_id, target_date) 해제
DELETE FROM verifications
 WHERE user_id LIKE 'loadtest-user-%'
   AND crew_id LIKE 'loadtest-crew-%'
   AND target_date = CURRENT_DATE;

-- 2. API 테스트용 챌린지 상태 복구 (completed_days 증가분 + SUCCESS 전이 되돌리기)
UPDATE challenges
   SET completed_days = 0,
       status = 'IN_PROGRESS',
       deadline = CURRENT_DATE + TIME '23:59:59'
 WHERE user_id LIKE 'loadtest-user-%'
   AND crew_id LIKE 'loadtest-crew-%';

-- 결과 확인
SELECT
    (SELECT count(*) FROM verifications
      WHERE user_id LIKE 'loadtest-user-%'
        AND crew_id LIKE 'loadtest-crew-%'
        AND target_date = CURRENT_DATE) AS remaining_today_verifications,
    (SELECT count(*) FROM challenges
      WHERE user_id LIKE 'loadtest-user-%'
        AND crew_id LIKE 'loadtest-crew-%'
        AND status = 'IN_PROGRESS'
        AND completed_days = 0) AS in_progress_zero_challenges;
