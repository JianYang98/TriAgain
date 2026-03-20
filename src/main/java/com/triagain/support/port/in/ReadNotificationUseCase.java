package com.triagain.support.port.in;

public interface ReadNotificationUseCase {

    /** 알림 읽음 처리 — 본인 알림만 가능 */
    void markAsRead(String notificationId, String userId);
}
