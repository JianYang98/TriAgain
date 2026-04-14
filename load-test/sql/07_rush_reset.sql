-- ============================================================
-- 러시 크루 멤버 초기화 (반복 실행용)
-- crew-rush.js 실행 전에 매번 실행하여 크루를 빈 상태로 리셋
-- ============================================================

-- 챌린지 삭제 (참가 시 자동 생성된 것)
DELETE FROM challenges WHERE crew_id LIKE 'loadtest-rush-crew-%';

-- 멤버 삭제
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-rush-crew-%';

-- 현재 멤버 수 리셋
UPDATE crews SET current_members = 0 WHERE id LIKE 'loadtest-rush-crew-%';

-- 날짜 갱신 (날짜가 바뀐 경우)
UPDATE crews SET
    start_date = CURRENT_DATE - 1,
    end_date = CURRENT_DATE + 5
WHERE id LIKE 'loadtest-rush-crew-%';

-- 결과 확인
SELECT id, current_members, max_members
FROM crews WHERE id LIKE 'loadtest-rush-crew-%'
ORDER BY id;
