package com.triagain.support.infra;

import com.triagain.support.port.out.NotificationSendPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("!prod")
@Slf4j
public class NoOpNotificationSendAdapter implements NotificationSendPort {

    /** dev/test 환경용 no-op FCM 발송 — 로그만 출력, 항상 true (토큰 유효) */
    @Override
    public boolean send(String fcmToken, String title, String body, Map<String, String> data) {
        log.debug("NoOp FCM: token={}, title={}, body={}, data={}", maskToken(fcmToken), title, body, data);
        return true;
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 10) return "***";
        return token.substring(0, 10) + "***";
    }
}
