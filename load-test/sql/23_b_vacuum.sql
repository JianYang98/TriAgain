-- ============================================================
-- B 테스트 run 크루 생성 후 VACUUM (한 행 UPDATE 반복이 남기는 dead tuple 정리, step0 §5-1)
-- 사용법: psql -X -v ON_ERROR_STOP=1 -f 23_b_vacuum.sql
-- VACUUM은 트랜잭션 안에서 실행할 수 없다 — 다른 SQL과 섞지 말고 psql -1/--single-transaction도 쓰지 않는다.
-- ============================================================
VACUUM (ANALYZE) crews, crew_members;
