package com.triagain.support.port.out;

import com.triagain.support.domain.model.Notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    /** 알림 페이지네이션 결과 */
    record NotificationSlice(List<Notification> notifications, boolean hasNext) {}

    /** 사용자별 알림 최신순 페이지네이션 조회 — page: 0-based */
    NotificationSlice findByUserId(String userId, int page, int size);

    long countUnreadByUserId(String userId);

    /** 알림 읽음 처리 — 단건 ID 기반 */
    void markAsRead(String notificationId);

    /** 지정 일시 이전 알림 일괄 삭제 — 스케줄러용 (30일 지난 알림 정리) */
    void deleteOlderThan(LocalDateTime dateTime);
}
