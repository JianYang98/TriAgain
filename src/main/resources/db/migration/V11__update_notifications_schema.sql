-- notifications 스키마 보강: target 컬럼 추가, 타입 조정, 인덱스 추가

ALTER TABLE notifications ADD COLUMN target_type VARCHAR(50);
ALTER TABLE notifications ADD COLUMN target_id   VARCHAR(36);

ALTER TABLE notifications ALTER COLUMN content TYPE VARCHAR(500);
ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(50);
ALTER TABLE notifications ALTER COLUMN is_read SET DEFAULT FALSE;

CREATE INDEX idx_notification_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notification_created      ON notifications (created_at);
