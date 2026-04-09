-- V5에서 users.id만 VARCHAR(64)로 확장했으나
-- FK로 user_id를 저장하는 다른 테이블들이 VARCHAR(36) 그대로 남아
-- Apple 유저(sub ~44자)가 크루 가입 등에서 실패하는 버그 수정

ALTER TABLE crews ALTER COLUMN creator_id TYPE VARCHAR(64);
ALTER TABLE crew_members ALTER COLUMN user_id TYPE VARCHAR(64);
ALTER TABLE challenges ALTER COLUMN user_id TYPE VARCHAR(64);
ALTER TABLE upload_session ALTER COLUMN user_id TYPE VARCHAR(64);
ALTER TABLE verifications ALTER COLUMN user_id TYPE VARCHAR(64);
ALTER TABLE reports ALTER COLUMN reporter_id TYPE VARCHAR(64);
ALTER TABLE reviews ALTER COLUMN reviewer_id TYPE VARCHAR(64);
ALTER TABLE notifications ALTER COLUMN user_id TYPE VARCHAR(64);
ALTER TABLE reactions ALTER COLUMN user_id TYPE VARCHAR(64);