-- crew_members 중복 가입 방지 — (crew_id, user_id) 멤버십 1개만 허용
-- 선행: (crew_id, user_id) 중복행 0건 확인 완료(2026-06-18).
CREATE UNIQUE INDEX uq_crew_members_crew_id_user_id
    ON crew_members (crew_id, user_id);
