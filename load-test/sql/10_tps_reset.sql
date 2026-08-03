-- ============================================================
-- TPS 크루 리셋 — 매 런 직전 필수 실행 (지시서 §4-1)
-- 07_rush_reset.sql 패턴 준용(challenges → crew_members → crews UPDATE)
-- + VACUUM (ANALYZE) + n_dead_tup 전/후 증빙(6런 반복 DELETE 블로트 = 순서 교락 통제)
--
-- 사용법: psql "$TEST_DB" -v ON_ERROR_STOP=1 -f sql/10_tps_reset.sql
-- ⚠️ psql에 -1/--single-transaction 플래그 금지 — VACUUM은 트랜잭션 블록 안에서 실행 불가.
--    (psql -f 기본 동작 = 문장별 autocommit이라 이 파일 그대로는 문제 없음)
-- ============================================================

-- [0] VACUUM 전 스냅샷 — 직전 런 DELETE로 쌓인 dead tuple 증빙 (첫 런·마지막 런 직전 값을 결과에 기록)
SELECT 'before_vacuum' AS phase, relname, n_live_tup, n_dead_tup, last_vacuum, last_autovacuum
FROM pg_stat_user_tables WHERE relname IN ('crews', 'crew_members', 'challenges');

-- [1] 챌린지 삭제 (참여 시 자동 생성분)
DELETE FROM challenges WHERE crew_id LIKE 'loadtest-tps-crew-%';

-- [2] 멤버 삭제
DELETE FROM crew_members WHERE crew_id LIKE 'loadtest-tps-crew-%';

-- [3] 가입 가능 상태 + 멤버수 + 날짜 리셋
-- status/allow_late_join 필수: isJoinableStatus() = RECRUITING OR (ACTIVE && allow_late_join).
-- 이 둘이 빠지면 한번 꼬인 상태가 리셋으로 안 풀려 CR003(모집 중 아님) 재발. (07_rush_reset.sql 동일 함정)
UPDATE crews SET
    status          = 'ACTIVE',
    allow_late_join = TRUE,
    current_members = 0,
    start_date      = CURRENT_DATE - 1,
    end_date        = CURRENT_DATE + 5
WHERE id LIKE 'loadtest-tps-crew-%';

-- [4] VACUUM — CASE A 1런당 성공 가입 ~4만 행 DELETE 누적 블로트 통제(§4-1).
-- 반드시 최상위 문장(DO 블록/트랜잭션 밖)이어야 실행 가능.
VACUUM (ANALYZE) crew_members;
VACUUM (ANALYZE) challenges;
VACUUM (ANALYZE) crews;

-- [5] VACUUM 후 스냅샷 — 블로트 통제 증빙 (정상: n_dead_tup ≈ 0)
SELECT 'after_vacuum' AS phase, relname, n_live_tup, n_dead_tup, last_vacuum, last_autovacuum
FROM pg_stat_user_tables WHERE relname IN ('crews', 'crew_members', 'challenges');

-- [6] joinable 상태 확인 (status='ACTIVE' + allow_late_join=t + current_members=0 이어야 정상)
SELECT id, status, allow_late_join, current_members, max_members
FROM crews WHERE id LIKE 'loadtest-tps-crew-%'
ORDER BY id LIMIT 10;

SELECT count(*) AS remaining_members FROM crew_members WHERE crew_id LIKE 'loadtest-tps-crew-%';
