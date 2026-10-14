-- ============================================================
-- B 테스트 run 크루 1개 + 리더 멤버 1행 생성 (재사용 금지)
-- 사용법:
--   psql -X -v ON_ERROR_STOP=1 -v crew_id=dbb-db50-01   -v invite_code=BD0501 -f 21_b_crew_create.sql
--   psql -X -v ON_ERROR_STOP=1 -v crew_id=dbb-warm-01   -v invite_code=BW0001 -v max_members=100 -f 21_b_crew_create.sql
--   psql -X -v ON_ERROR_STOP=1 -v crew_id=dbb-smoke-01  -v invite_code=BS0001 -v max_members=5   -f 21_b_crew_create.sql
-- 변수: crew_id(필수, ^dbb-[A-Za-z0-9-]{1,31}$) · invite_code(필수, 정확히 6자)
--       max_members(선택, 기본 1000. 1000이 아닌 값은 dbb-smoke-NN·dbb-warm-NN만, 범위 1~1000)
-- 선행: 20_b_leader_user.sql
-- 같은 crew_id·invite_code가 이미 있으면 PK/UNIQUE 위반으로 실패한다 (ON CONFLICT 없음).
-- 크루·멤버 INSERT는 DO 블록 하나 = 한 트랜잭션이라 실패 시 부분 데이터가 남지 않는다.
-- Crew.create()와 같은 모양: current_members=1, 리더 1행(role LEADER).
-- ============================================================
\set ON_ERROR_STOP on

\if :{?crew_id}
\else
  DO $$ BEGIN RAISE EXCEPTION '[21] psql 변수 crew_id 가 필요하다 (-v crew_id=dbb-...)'; END $$;
\endif
\if :{?invite_code}
\else
  DO $$ BEGIN RAISE EXCEPTION '[21] psql 변수 invite_code 가 필요하다 (-v invite_code=XXXXXX, 6자)'; END $$;
\endif
\if :{?max_members}
\else
  \set max_members 1000
\endif

SET dbb.crew_id = :'crew_id';
SET dbb.invite_code = :'invite_code';
SET dbb.max_members = :'max_members';

DO $$
DECLARE
    v_crew_id TEXT := current_setting('dbb.crew_id');
    v_invite  TEXT := current_setting('dbb.invite_code');
    v_max     INT  := current_setting('dbb.max_members')::int;
BEGIN
    IF v_crew_id !~ '^dbb-[A-Za-z0-9-]{1,31}$' THEN
        RAISE EXCEPTION '[21] crew_id 형식 위반: % (^dbb-[A-Za-z0-9-]{1,31}$)', v_crew_id;
    END IF;
    IF v_invite !~ '^[A-Za-z0-9]{6}$' THEN
        RAISE EXCEPTION '[21] invite_code는 영숫자 정확히 6자: %', v_invite;
    END IF;
    IF v_max < 1 OR v_max > 1000 THEN
        RAISE EXCEPTION '[21] max_members 범위 위반: % (1~1000)', v_max;
    END IF;
    IF v_max <> 1000 AND v_crew_id !~ '^dbb-(smoke|warm)-[0-9]{2}$' THEN
        RAISE EXCEPTION '[21] max_members=%는 dbb-smoke-NN·dbb-warm-NN 크루만 허용 (본 측정은 1000): %', v_max, v_crew_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE id = 'loadtest-leader') THEN
        RAISE EXCEPTION '[21] 크루장 loadtest-leader 없음 — 20_b_leader_user.sql 먼저';
    END IF;
    -- crews에 없어도 이전 잔재 멤버 행이 있으면 재사용으로 본다 (I2·I3 오염 방지)
    IF EXISTS (SELECT 1 FROM crew_members WHERE crew_id = v_crew_id) THEN
        RAISE EXCEPTION '[21] crew_members에 % 행이 이미 있다 — 재사용 금지', v_crew_id;
    END IF;

    INSERT INTO crews (
        id, creator_id, name, goal, verification_content, verification_type,
        max_members, current_members, status, start_date, end_date,
        allow_late_join, invite_code, created_at, category, visibility
    ) VALUES (
        v_crew_id, 'loadtest-leader', 'B테스트 가입 크루', 'DB baseline B 가입 부하 측정',
        '부하테스트 크루 - 인증 없음', 'TEXT',
        v_max, 1, 'RECRUITING',
        CURRENT_DATE + 365,   -- 먼 미래: 활성화 스케줄러·보정 러너 대상 아님
        CURRENT_DATE + 395,   -- 참여 마감(end_date - 3일)이 실험일보다 한참 뒤
        FALSE, v_invite, NOW(), 'ETC', 'PUBLIC'
    );

    INSERT INTO crew_members (id, user_id, crew_id, role, joined_at)
    VALUES ('CRMB-' || substr(md5(v_crew_id), 1, 16), 'loadtest-leader', v_crew_id, 'LEADER', NOW());
END $$;

SELECT c.id, c.invite_code, c.status, c.visibility, c.max_members, c.current_members,
       c.start_date, c.end_date, c.allow_late_join, c.version,
       (SELECT count(*) FROM crew_members m WHERE m.crew_id = c.id) AS member_rows
FROM crews c WHERE c.id = :'crew_id';
