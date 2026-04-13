package com.triagain.support.port.in;

import java.time.LocalDateTime;
import java.util.List;

public interface GetNotificationsUseCase {

    /** 내 알림 목록 조회 — 최신순, 페이지네이션, isRead 필터 (null이면 전체) */
    NotificationListResult getNotifications(String userId, Boolean isRead, int page, int size);

    /** 안 읽은 알림 수 조회 — 뱃지 표시용 */
    long getUnreadCount(String userId);

    record NotificationListResult(List<NotificationItem> notifications, boolean hasNext) {}

    record NotificationItem(
            String id,
            String type,
            String title,
            String content,
            boolean isRead,
            String targetType,
            String targetId,
            LocalDateTime createdAt
    ) {}
}
