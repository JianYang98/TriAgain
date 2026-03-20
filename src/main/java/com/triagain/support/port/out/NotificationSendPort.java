package com.triagain.support.port.out;

import java.util.Map;

public interface NotificationSendPort {

    /** FCM 푸시 알림 전송 — 토큰 기반 단건 발송 */
    void send(String fcmToken, String title, String body, Map<String, String> data);
}
