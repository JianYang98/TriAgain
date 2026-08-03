-- ============================================================
-- 러시 크루 완전 삭제 (teardown) — 재빌드용
-- 크루를 행까지 통째로 DELETE한다. 이후 07_rush_crews.sql로 재생성.
--
--   ⚠️ 파괴적 작업. 일상 루프(멤버/챌린지만 비우고 크루 유지)는
--      07_rush_reset.sql 을 쓴다. 이 파일은 시드 정의를 바꿨거나
--      크루 상태가 꼬여서 "행을 통째로 새로" 만들 때만 사용.
--
-- 대상: loadtest-rush-crew-% 만 (유저·다른 시나리오 크루는 무관).
-- FK 제약이 DB에 없음 → 자식 테이블부터 부모(crews) 순서로 삭제.
--   순서를 바꾸면 고아행이 남는다.
--
-- 안전 실행(권장): psql 인터랙티브에서 BEGIN; 으로 감싸 SELECT 0 확인 후 COMMIT;
--   psql -f 로 돌리면 각 문이 자동 커밋된다(마지막 SELECT는 결과 확인용).
-- ============================================================

-- 인증 계열 (러시는 보통 0건이지만 순서상 먼저)
DELETE FROM reviews        WHERE report_id IN (
    SELECT id FROM reports WHERE verification_id IN (
        SELECT id FROM verifications WHERE crew_id LIKE 'loadtest-rush-crew-%'));
DELETE FROM reports        WHERE verification_id IN (
    SELECT id FROM verifications WHERE crew_id LIKE 'loadtest-rush-crew-%');
DELETE FROM reactions      WHERE verification_id IN (
    SELECT id FROM verifications WHERE crew_id LIKE 'loadtest-rush-crew-%');
DELETE FROM verifications   WHERE crew_id LIKE 'loadtest-rush-crew-%';
DELETE FROM upload_session  WHERE crew_id LIKE 'loadtest-rush-crew-%';

-- 직접 자식
DELETE FROM challenges      WHERE crew_id LIKE 'loadtest-rush-crew-%';
DELETE FROM crew_members    WHERE crew_id LIKE 'loadtest-rush-crew-%';
DELETE FROM notifications   WHERE target_type = 'CREW' AND target_id LIKE 'loadtest-rush-crew-%';

-- 부모
DELETE FROM crews           WHERE id LIKE 'loadtest-rush-crew-%';

-- 결과 확인: 0 이어야 정상
SELECT COUNT(*) AS remaining_rush_crews
FROM crews WHERE id LIKE 'loadtest-rush-crew-%';
