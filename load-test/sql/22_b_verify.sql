-- ============================================================
-- B 테스트 run 직후 정합성 검증 (읽기 전용) — 결과를 sql-after.txt로 보존
-- 사용법:
--   psql -X -v ON_ERROR_STOP=1 -v crew_id=dbb-db50-01 -v k6_201=999 -f 22_b_verify.sql > sql-after.txt
--   (워밍업·스모크: -v expected_total=<정원>)
-- 변수: crew_id(필수) · k6_201(필수, k6 join_201 건수) · expected_total(선택, 기본 1000)
-- 출력: 불변식 1개당 1행. 열 = item, result, verdict + 숫자 값.
--   result: OK | FAIL (I6만 OK | NOT_FULL — 정보 표시, run 중간에는 NOT_FULL이 정상)
--   verdict: I6을 뺀 항목 중 FAIL인 첫 항목 이름, 없으면 OK (모든 행에 같은 값)
--   I1 정원 초과 없음      rows <= max_members
--   I2 카운터 일치         counter = rows
--   I3 중복 없음           rows = distinct_users   (V22 유니크 인덱스가 막는 항목)
--   I4 리더 1명            leaders = 1 이고 그 사용자가 loadtest-leader
--   I5 풀 밖 멤버 없음     outsiders = 0 (role<>LEADER 인데 loadtest-user-% 아닌 행)
--   I6 정원 도달 (정보)    rows = expected_total
--   I7 k6와 일치           rows = k6_201 + 1 (+1은 리더)
-- 데이터를 바꾸지 않는다 (default_transaction_read_only). 크루가 없으면 실패한다.
-- ============================================================
\set ON_ERROR_STOP on
SET default_transaction_read_only = on;

\if :{?crew_id}
\else
  DO $$ BEGIN RAISE EXCEPTION '[22] psql 변수 crew_id 가 필요하다'; END $$;
\endif
\if :{?k6_201}
\else
  DO $$ BEGIN RAISE EXCEPTION '[22] psql 변수 k6_201 이 필요하다 (k6 join_201 건수)'; END $$;
\endif
\if :{?expected_total}
\else
  \set expected_total 1000
\endif

SELECT count(*) = 1 AS dbb_crew_found FROM crews WHERE id = :'crew_id' \gset
\if :dbb_crew_found
\else
  DO $$ BEGIN RAISE EXCEPTION '[22] crews에 해당 crew_id가 없다'; END $$;
\endif

WITH s AS (
    SELECT c.id AS crew_id,
           c.max_members,
           c.current_members AS counter,
           count(m.id) AS rows,
           count(DISTINCT m.user_id) AS distinct_users,
           count(*) FILTER (WHERE m.role = 'LEADER') AS leaders,
           count(*) FILTER (WHERE m.role = 'LEADER' AND m.user_id <> 'loadtest-leader') AS foreign_leaders,
           count(*) FILTER (WHERE m.role <> 'LEADER' AND m.user_id NOT LIKE 'loadtest-user-%') AS outsiders,
           :'expected_total'::int AS expected_total,
           :'k6_201'::int AS k6_201
    FROM crews c
    LEFT JOIN crew_members m ON m.crew_id = c.id
    WHERE c.id = :'crew_id'
    GROUP BY c.id, c.max_members, c.current_members
), checks AS (
    SELECT s.*, v.ord, v.item, v.result
    FROM s CROSS JOIN LATERAL (VALUES
        (1, 'I1_no_overflow',   CASE WHEN s.rows <= s.max_members              THEN 'OK' ELSE 'FAIL' END),
        (2, 'I2_counter_match', CASE WHEN s.counter = s.rows                   THEN 'OK' ELSE 'FAIL' END),
        (3, 'I3_no_duplicate',  CASE WHEN s.rows = s.distinct_users            THEN 'OK' ELSE 'FAIL' END),
        (4, 'I4_one_leader',    CASE WHEN s.leaders = 1 AND s.foreign_leaders = 0 THEN 'OK' ELSE 'FAIL' END),
        (5, 'I5_pool_only',     CASE WHEN s.outsiders = 0                      THEN 'OK' ELSE 'FAIL' END),
        (6, 'I6_full_info',     CASE WHEN s.rows = s.expected_total            THEN 'OK' ELSE 'NOT_FULL' END),
        (7, 'I7_k6_match',      CASE WHEN s.rows = s.k6_201 + 1                THEN 'OK' ELSE 'FAIL' END)
    ) AS v(ord, item, result)
)
SELECT item, result,
       coalesce((SELECT min(item) FROM checks f WHERE f.result = 'FAIL'), 'OK') AS verdict,
       crew_id, max_members, counter, rows, distinct_users, leaders, outsiders, expected_total, k6_201
FROM checks
ORDER BY ord;
