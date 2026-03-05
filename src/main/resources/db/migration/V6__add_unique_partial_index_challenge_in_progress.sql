-- 동시 요청 시 같은 유저/크루에 IN_PROGRESS 챌린지 2개 생성 방지
CREATE UNIQUE INDEX uk_challenges_user_crew_in_progress
    ON challenges (user_id, crew_id)
    WHERE status = 'IN_PROGRESS';
