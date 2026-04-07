package com.triagain.verification.infra;

import com.triagain.crew.port.in.CrewMembershipQueryUseCase;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationMessageTemplate;
import com.triagain.support.domain.vo.NotificationMessageTemplate.NotificationMessage;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.port.out.FcmTokenCleanupPort;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationSendPort;
import com.triagain.user.port.out.UserRepositoryPort;
import com.triagain.verification.port.out.VerificationNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Verification → Support 컨텍스트 간 알림 어댑터 — 챌린지 성공 알림 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationNotificationAdapter implements VerificationNotificationPort {

    private final CrewMembershipQueryUseCase crewMembershipQueryUseCase;
    private final NotificationRepositoryPort notificationRepositoryPort;
    private final NotificationSendPort notificationSendPort;
    private final FcmTokenCleanupPort fcmTokenCleanupPort;
    private final UserRepositoryPort userRepositoryPort;

    @Override
    public void sendChallengeSuccessNotification(String userId, String crewId) {
        try {
            String crewName = crewMembershipQueryUseCase.getCrewName(crewId);
            NotificationMessage msg = NotificationMessageTemplate.challengeSuccess(crewName);

            Notification notification = Notification.create(
                    userId, NotificationType.CHALLENGE_SUCCESS,
                    msg.title(), msg.content(),
                    NotificationTargetType.CREW, crewId
            );
            notificationRepositoryPort.save(notification);

            // FCM best-effort 발송
            userRepositoryPort.findById(userId).ifPresent(user -> {
                if (user.getFcmToken() != null) {
                    try {
                        boolean tokenValid = notificationSendPort.send(
                                user.getFcmToken(), msg.title(), msg.content(),
                                Map.of("type", "CHALLENGE_SUCCESS", "crewId", crewId));
                        if (!tokenValid) {
                            fcmTokenCleanupPort.clearFcmToken(userId);
                        }
                    } catch (Exception e) {
                        log.warn("챌린지 성공 FCM 발송 실패 [userId={}]: {}", userId, e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            log.error("챌린지 성공 알림 저장 실패 [userId={}, crewId={}]: {}", userId, crewId, e.getMessage());
        }
    }
}