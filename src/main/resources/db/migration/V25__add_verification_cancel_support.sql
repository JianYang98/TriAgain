-- ① 슬롯당 제출 회차 (이력 순서 + 취소·수정 상한 + 감사)
--    기존 행은 uk_verifications_user_crew_date가 (user,crew,date) 유일성을 보장했으므로
--    전부 1이 정답 — 별도 백필 쿼리 불필요 (step0 C1)
ALTER TABLE verifications ADD COLUMN slot_attempt INT NOT NULL DEFAULT 1;

-- ② 하루 1회 유니크를 partial 로 교체
--    CANCELLED 행이 슬롯을 점유하지 않도록 하여 같은 날 재인증·수정(치환)을 허용한다
ALTER TABLE verifications DROP CONSTRAINT uk_verifications_user_crew_date;

CREATE UNIQUE INDEX uk_verifications_user_crew_date_active
    ON verifications (user_id, crew_id, target_date)
    WHERE status <> 'CANCELLED';
