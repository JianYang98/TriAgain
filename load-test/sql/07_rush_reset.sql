-- ============================================================
-- 러시 크루 멤버 초기화 (반복 실행용)
-- crew-rush.js 실행 전에 매번 실행하여 크루를 빈 상태로 리셋
-- ============================================================

-- 챌린지 삭제 (참가 시 자동 생성된 것)
DELETE FROM challenges WHERE crew_id LIKE 'loadtest-rush-crew-%';

-- 멤버 삭제
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-rush-crew-%';

-- 가입 가능 상태 + 멤버수 + 날짜 리셋
-- status/allow_late_join 필수: isJoinableStatus() = RECRUITING OR (ACTIVE && allow_late_join).
-- 이 둘이 빠지면 한번 꼬인 상태가 리셋으로 안 풀려 CR003(모집 중 아님) 재발.
UPDATE crews SET
    status          = 'ACTIVE',
    allow_late_join = TRUE,
    current_members = 0,
    start_date      = CURRENT_DATE - 1,
    end_date        = CURRENT_DATE + 5
WHERE id LIKE 'loadtest-rush-crew-%';

-- 결과 확인 (status=ACTIVE, allow_late_join=t 여야 joinable)
SELECT id, status, allow_late_join, current_members, max_members
FROM crews WHERE id LIKE 'loadtest-rush-crew-%'
ORDER BY id;
