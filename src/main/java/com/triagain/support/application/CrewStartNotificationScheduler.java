package com.triagain.support.application;

import com.triagain.support.domain.vo.NotificationMessageTemplate;
import com.triagain.support.domain.vo.NotificationMessageTemplate.NotificationMessage;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.port.out.FcmTokenCleanupPort;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationSendPort;
import com.triagain.support.port.out.NotificationTargetQueryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort.CrewStartTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrewStartNotificationScheduler {

    private final NotificationTargetQueryPort notificationTargetQueryPort;
    private final NotificationRepositoryPort notificationRepositoryPort;
    private final NotificationSendPort notificationSendPort;
    private final FcmTokenCleanupPort fcmTokenCleanupPort;
    private final TransactionTemplate transactionTemplate;

    /** 크루 시작 알림 — 매일 09:00, 오늘 시작된 크루의 전체 멤버에게 알림 */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendCrewStartNotifications() {
        List<CrewStartTarget> targets = notificationTargetQueryPort.findCrewStartTargets(LocalDate.now());
        if (targets.isEmpty()) return;

        int successCount = 0;
        List<String> failedUserIds = new ArrayList<>();

        for (CrewStartTarget target : targets) {
            try {
                NotificationMessage msg = NotificationMessageTemplate.crewStarted(target.crewName());

                transactionTemplate.executeWithoutResult(status -> {
                    Notification notification = Notification.create(
                            target.userId(), NotificationType.CREW_STARTED,
                            msg.title(), msg.content(),
                            NotificationTargetType.CREW, target.crewId()
                    );
                    notificationRepositoryPort.save(notification);
                });

                if (target.fcmToken() != null) {
                    boolean tokenValid = notificationSendPort.send(target.fcmToken(), msg.title(), msg.content(),
                            Map.of("type", "CREW_STARTED", "crewId", target.crewId()));
                    if (!tokenValid) {
                        fcmTokenCleanupPort.clearFcmToken(target.userId());
                    }
                }
                successCount++;
            } catch (Exception e) {
                failedUserIds.add(target.userId());
                log.error("크루 시작 알림 발송 실패 [userId={}, crewId={}]: {}",
                        target.userId(), target.crewId(), e.getMessage(), e);
            }
        }

        log.info("크루 시작 알림 발송 완료: 전체={}, 성공={}, 실패={}{}",
                targets.size(), successCount, failedUserIds.size(),
                failedUserIds.isEmpty() ? "" : " | 실패 userId: " + String.join(", ", failedUserIds));
    }
}
