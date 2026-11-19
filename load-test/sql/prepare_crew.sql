\set ON_ERROR_STOP on
\echo PREPARE_TRANSACTION_BEGIN
BEGIN;
SET LOCAL TIME ZONE 'Asia/Seoul';
SET LOCAL search_path TO :"schema";
SELECT set_config('prepare.expected_db', :'expected_db', true),
       set_config('prepare.expected_user', :'expected_user', true),
       set_config('prepare.crew_id', :'crew_id', true),
       set_config('prepare.crew_name', :'crew_name', true),
       set_config('prepare.invite_code', :'invite_code', true),
       set_config('prepare.member_id', :'member_id', true),
       set_config('prepare.capacity', :'capacity', true),
       set_config('prepare.started_at', :'started_at', true),
       set_config('prepare.expected_schema', :'schema', true),
       set_config('prepare.start_date', :'start_date', true),
       set_config('prepare.end_date', :'end_date', true);

DO $guard$
BEGIN
  IF current_database() <> current_setting('prepare.expected_db') OR current_user <> current_setting('prepare.expected_user') THEN
    RAISE EXCEPTION 'connected target mismatch';
  END IF;
  IF current_schema() IS DISTINCT FROM current_setting('prepare.expected_schema') THEN
    RAISE EXCEPTION 'configured schema is not active';
  END IF;
  IF current_setting('prepare.crew_id') !~ '^dbb-[0-9]{8}-[0-9]{6}$' THEN RAISE EXCEPTION 'invalid crew id'; END IF;
END
$guard$;

SELECT EXISTS (SELECT 1 FROM :"schema".crews WHERE id = :'crew_id')
    OR EXISTS (SELECT 1 FROM :"schema".crew_members WHERE crew_id = :'crew_id')
    OR EXISTS (SELECT 1 FROM :"schema".challenges WHERE crew_id = :'crew_id')
    OR EXISTS (SELECT 1 FROM :"schema".verifications WHERE crew_id = :'crew_id')
    OR EXISTS (SELECT 1 FROM :"schema".upload_session WHERE crew_id = :'crew_id') AS residue \gset
\if :residue
  DO $$ BEGIN RAISE EXCEPTION 'crew id or related residue already exists'; END $$;
\endif
SELECT EXISTS (SELECT 1 FROM :"schema".users WHERE id = 'loadtest-leader' AND deleted_at IS NULL) AS leader_ok \gset
\if :leader_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'active loadtest-leader is missing'; END $$;
\endif
SELECT EXISTS (SELECT 1 FROM :"schema".crews WHERE invite_code = :'invite_code')
    OR EXISTS (SELECT 1 FROM :"schema".crew_members WHERE id = :'member_id') AS collision \gset
\if :collision
  DO $$ BEGIN RAISE EXCEPTION 'invite code or member id collision'; END $$;
\endif

INSERT INTO :"schema".crews (
  id, creator_id, name, goal, verification_content, verification_type,
  max_members, current_members, status, start_date, end_date,
  allow_late_join, invite_code, created_at, category, visibility
) VALUES (
  :'crew_id', 'loadtest-leader', :'crew_name', '부하 테스트 크루', '테스트 인증', 'TEXT',
  :'capacity'::integer, 1, 'RECRUITING', :'start_date'::date, :'end_date'::date,
  false, :'invite_code', :'started_at'::timestamp, 'ETC', 'PUBLIC'
);
INSERT INTO :"schema".crew_members (id, user_id, crew_id, role, joined_at)
VALUES (:'member_id', 'loadtest-leader', :'crew_id', 'LEADER', :'started_at'::timestamp);

SELECT (SELECT count(*) = 1 FROM :"schema".crews c WHERE c.id = :'crew_id'
        AND c.name = :'crew_name' AND c.creator_id = 'loadtest-leader'
        AND c.max_members = :'capacity'::integer AND c.current_members = 1
        AND c.status = 'RECRUITING' AND c.visibility = 'PUBLIC'
        AND c.verification_type = 'TEXT' AND c.category = 'ETC' AND c.allow_late_join = false
        AND c.start_date = :'start_date'::date AND c.end_date = :'end_date'::date
        AND c.invite_code = :'invite_code')
    AND (SELECT count(*) = 1 FROM :"schema".crew_members m WHERE m.crew_id = :'crew_id'
         AND m.id = :'member_id' AND m.user_id = 'loadtest-leader' AND m.role = 'LEADER')
    AND NOT EXISTS (SELECT 1 FROM :"schema".challenges WHERE crew_id = :'crew_id')
    AND NOT EXISTS (SELECT 1 FROM :"schema".verifications WHERE crew_id = :'crew_id')
    AND NOT EXISTS (SELECT 1 FROM :"schema".upload_session WHERE crew_id = :'crew_id') AS verified \gset
\if :verified
\else
  DO $$ BEGIN RAISE EXCEPTION 'prepared crew verification failed'; END $$;
\endif
\echo PREPARE_COMMIT_ATTEMPT
COMMIT;
\echo PREPARE_COMMIT_CONFIRMED
