-- 알림 전체 읽음 / 읽음 필터 조회 성능 개선
CREATE INDEX idx_notification_user_is_read ON notifications (user_id, is_read);
