-- ============================================================
-- 러시 크루 정합성 검증 (락 테스트용)
-- 각 k6 런 직후 · 다음 07_rush_reset.sql 실행 前에 돌릴 것.
--   reset → k6 run → (이 파일) → reset → 다음 런
-- reset이 크루를 비우므로 런 직후에 찍지 않으면 결과가 날아간다.
-- 최소한 경합 최고점(vu500·vu700) 직후엔 반드시 실행.
--
-- 검증하는 3대 불변식:
--   1. actual_rows <= max_members      : 정원 초과(11명째 진입) 없나
--   2. current_members == actual_rows  : 역정규화 카운터가 실제 행과 어긋났나 (경합 시 최빈 버그)
--   3. actual_rows == distinct_users   : 같은 유저 중복 멤버십 (V22 유니크가 막아야 함, 확인 사살)
--
-- join_success=10 은 "서버가 201을 10번 뱉었다"는 클라 관측이고,
-- 이 쿼리는 DB 지상 진실 — 응답과 실제 저장 상태의 일치까지 닫는다.
--
-- 실행: psql "$DB_URL" -f load-test/sql/07_rush_verify.sql
-- ============================================================

SELECT
    c.id,
    c.max_members,
    c.current_members AS counter,                                                       -- 역정규화 카운터
    (SELECT count(*)                  FROM crew_members m WHERE m.crew_id = c.id) AS actual_rows,
    (SELECT count(DISTINCT m.user_id) FROM crew_members m WHERE m.crew_id = c.id) AS distinct_users,
    CASE
        WHEN (SELECT count(*) FROM crew_members m WHERE m.crew_id = c.id) > c.max_members
            THEN '❌ 정원초과(over-capacity)'
        WHEN c.current_members <> (SELECT count(*) FROM crew_members m WHERE m.crew_id = c.id)
            THEN '❌ 카운터-실제행 불일치'
        WHEN (SELECT count(*) FROM crew_members m WHERE m.crew_id = c.id)
             <> (SELECT count(DISTINCT m.user_id) FROM crew_members m WHERE m.crew_id = c.id)
            THEN '❌ 중복가입'
        ELSE '✅ OK'
    END AS verdict
FROM crews c
WHERE c.id LIKE 'loadtest-rush-crew-%'
ORDER BY c.id;

-- 위 verdict가 ❌ 이면 아래로 실제 위반 행을 확인한다.

-- (참고) 중복 멤버십이 있으면 여기서 crew_id·user_id·건수가 뜬다 (정상: 0행)
SELECT crew_id, user_id, count(*) AS dup_cnt
FROM crew_members
WHERE crew_id LIKE 'loadtest-rush-crew-%'
GROUP BY crew_id, user_id
HAVING count(*) > 1
ORDER BY crew_id, user_id;
