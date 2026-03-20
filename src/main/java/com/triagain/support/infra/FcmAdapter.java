package com.triagain.support.infra;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.support.port.out.NotificationSendPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class FcmAdapter implements NotificationSendPort {

    private final FirebaseMessaging firebaseMessaging;

    /** FCM 푸시 알림 전송 — prod 환경에서만 활성화, 실패 시 3회 재시도 */
    @Retryable(
            retryFor = {BusinessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public void send(String fcmToken, String title, String body, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .build();

        try {
            String messageId = firebaseMessaging.send(message);
            log.info("FCM 발송 성공: messageId={}", messageId);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("FCM 영구 오류 (재시도 불가): token={}, errorCode={}", maskToken(fcmToken), errorCode);
                return;
            }
            log.warn("FCM 발송 실패, 재시도 예정: token={}, error={}", maskToken(fcmToken), e.getMessage());
            throw new BusinessException(ErrorCode.FCM_SEND_FAILED);
        }
    }

    /** FCM 발송 최종 실패 핸들러 — 3회 재시도 후 호출 */
    @Recover
    public void recoverSend(BusinessException e, String fcmToken, String title, String body, Map<String, String> data) {
        log.error("FCM 발송 최종 실패 (3회 재시도 후): token={}", maskToken(fcmToken), e);
        throw e;
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 10) return "***";
        return token.substring(0, 10) + "***";
    }
}
