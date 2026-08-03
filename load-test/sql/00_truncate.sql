-- ============================================================
-- 부하테스트 데이터 초기화 (TRUNCATE)
-- 사용법: psql -f 00_truncate.sql
-- 주의: loadtest- prefix 데이터만 삭제, 기존 유저는 보존
-- ============================================================

-- FK 의존성 역순으로 삭제
DELETE FROM dead_letters WHERE target_id LIKE 'loadtest-%';
DELETE FROM reviews WHERE report_id IN (
    SELECT id FROM reports WHERE reporter_id LIKE 'loadtest-%'
);
DELETE FROM reports WHERE reporter_id LIKE 'loadtest-%';
DELETE FROM reactions WHERE user_id LIKE 'loadtest-%';
DELETE FROM notifications WHERE user_id LIKE 'loadtest-%';
DELETE FROM verifications WHERE user_id LIKE 'loadtest-%';
DELETE FROM upload_session WHERE user_id LIKE 'loadtest-%';
DELETE FROM challenges WHERE user_id LIKE 'loadtest-%';
DELETE FROM crew_members WHERE user_id LIKE 'loadtest-%';
DELETE FROM crews WHERE creator_id LIKE 'loadtest-%';
DELETE FROM users WHERE id LIKE 'loadtest-%';

-- 결과 확인
SELECT 'truncate done' AS result,
       (SELECT count(*) FROM users WHERE id LIKE 'loadtest-%') AS remaining_users;
