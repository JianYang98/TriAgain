-- ============================================================
-- B 테스트 run 크루 정리 (선택, 수동 실행 전용 — 진단이 끝난 뒤 사용자가 실행)
-- 사용법: psql -X -v ON_ERROR_STOP=1 -v crew_id=dbb-db50-01 -v confirm=yes -f 29_b_cleanup_crew.sql
-- 변수: crew_id(필수, ^dbb-[A-Za-z0-9-]{1,31}$ 아니면 실패) · confirm(yes가 아니면 아무것도 지우지 않고 종료)
--
-- 삭제 대상 (FK 0건 DB라 자식 → 부모 순서):
--   crew_id 컬럼 보유 테이블 [V1~V26 grep]: crew_members(V1) · challenges(V1) · verifications(V1) · upload_session(V8)
--   간접 참조: reviews → reports → reactions (verifications.id 경유) · notifications(target_type='CREW', target_id)
--   순서는 제품 삭제 경로 CrewJpaAdapter.deleteCrewWithAssociations와 같다
--   (단, upload_session은 제품처럼 crew_id를 NULL로 두지 않고 행을 지운다 — 테스트 데이터라서).
-- 단언: 삭제 후 information_schema에서 crew_id 컬럼을 가진 모든 테이블(새로 생긴 테이블 포함)과
--   crews · notifications · 간접 참조 테이블의 해당 행 0건. 아니면 예외 → DO 블록 전체 롤백.
-- ============================================================
\set ON_ERROR_STOP on

\if :{?crew_id}
\else
  DO $$ BEGIN RAISE EXCEPTION '[29] psql 변수 crew_id 가 필요하다'; END $$;
\endif
\if :{?confirm}
\else
  \set confirm no
\endif

SET dbb.crew_id = :'crew_id';
DO $$
BEGIN
    IF current_setting('dbb.crew_id') !~ '^dbb-[A-Za-z0-9-]{1,31}$' THEN
        RAISE EXCEPTION '[29] dbb- 크루가 아니다 — 삭제 거부: %', current_setting('dbb.crew_id');
    END IF;
END $$;

SELECT :'confirm' = 'yes' AS dbb_confirmed \gset
\if :dbb_confirmed
\else
  \echo '[29] -v confirm=yes 가 아니므로 아무것도 지우지 않고 종료한다'
  \quit
\endif

DO $$
DECLARE
    v_crew_id TEXT := current_setting('dbb.crew_id');
    v_ver_ids TEXT[];
    v_rep_ids TEXT[];
    v_n BIGINT;
    r RECORD;
BEGIN
    SELECT coalesce(array_agg(id), '{}') INTO v_ver_ids FROM verifications WHERE crew_id = v_crew_id;
    SELECT coalesce(array_agg(id), '{}') INTO v_rep_ids FROM reports WHERE verification_id = ANY (v_ver_ids);

    DELETE FROM reviews        WHERE report_id = ANY (v_rep_ids);       GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted reviews: %', v_n;
    DELETE FROM reports        WHERE id = ANY (v_rep_ids);              GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted reports: %', v_n;
    DELETE FROM reactions      WHERE verification_id = ANY (v_ver_ids); GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted reactions: %', v_n;
    DELETE FROM upload_session WHERE crew_id = v_crew_id;               GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted upload_session: %', v_n;
    DELETE FROM verifications  WHERE crew_id = v_crew_id;               GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted verifications: %', v_n;
    DELETE FROM challenges     WHERE crew_id = v_crew_id;               GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted challenges: %', v_n;
    DELETE FROM notifications  WHERE target_type = 'CREW' AND target_id = v_crew_id; GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted notifications: %', v_n;
    DELETE FROM crew_members   WHERE crew_id = v_crew_id;               GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted crew_members: %', v_n;
    DELETE FROM crews          WHERE id = v_crew_id;                    GET DIAGNOSTICS v_n = ROW_COUNT; RAISE NOTICE 'deleted crews: %', v_n;

    -- 단언 1: crew_id 컬럼을 가진 모든 테이블 (목록에 없는 새 테이블에 행이 있으면 여기서 실패)
    FOR r IN SELECT table_name FROM information_schema.columns
             WHERE table_schema = current_schema() AND column_name = 'crew_id'
             ORDER BY table_name
    LOOP
        EXECUTE format('SELECT count(*) FROM %I WHERE crew_id = $1', r.table_name) INTO v_n USING v_crew_id;
        IF v_n <> 0 THEN
            RAISE EXCEPTION '[29] 삭제 후 %.crew_id = % 행 %건 남음 — 롤백', r.table_name, v_crew_id, v_n;
        END IF;
        RAISE NOTICE 'assert 0 rows: %', r.table_name;
    END LOOP;

    -- 단언 2: crew_id 컬럼이 없는 참조
    SELECT (SELECT count(*) FROM crews WHERE id = v_crew_id)
         + (SELECT count(*) FROM notifications WHERE target_type = 'CREW' AND target_id = v_crew_id)
         + (SELECT count(*) FROM reactions WHERE verification_id = ANY (v_ver_ids))
         + (SELECT count(*) FROM reports WHERE id = ANY (v_rep_ids))
         + (SELECT count(*) FROM reviews WHERE report_id = ANY (v_rep_ids))
      INTO v_n;
    IF v_n <> 0 THEN
        RAISE EXCEPTION '[29] 삭제 후 crews·notifications·간접 참조 행 %건 남음 — 롤백', v_n;
    END IF;
    RAISE NOTICE 'assert 0 rows: crews, notifications, reactions, reports, reviews — cleanup OK: %', v_crew_id;
END $$;
