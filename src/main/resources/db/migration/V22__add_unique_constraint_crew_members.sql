-- crew_members 중복 가입 방지 — 전략 C(조건부 UPDATE)의 동시성 안전망
-- 선행(prod 적용 시): (crew_id, user_id) 중복행 0건 확인 필수. 이 작업은 feat/load-test 한정.
CREATE UNIQUE INDEX uq_crew_members_crew_id_user_id
    ON crew_members (crew_id, user_id);
