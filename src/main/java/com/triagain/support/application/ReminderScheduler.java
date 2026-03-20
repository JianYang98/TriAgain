package com.triagain.support.application;

import com.triagain.support.domain.vo.NotificationMessageTemplate;
import com.triagain.support.domain.vo.NotificationMessageTemplate.NotificationMessage;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationSendPort;
import com.triagain.support.port.out.NotificationTargetQueryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort.ReminderTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final NotificationTargetQueryPort notificationTargetQueryPort;
    private final NotificationRepositoryPort notificationRepositoryPort;
    private final NotificationSendPort notificationSendPort;
    private final TransactionTemplate transactionTemplate;

    /** 인증 마감 임박 리마인더 — 매 15분, deadlineTime 15~30분 전 미인증 유저 대상 */
    @Scheduled(cron = "0 0/15 * * * *")
    public void sendReminders() {
        LocalTime now = LocalTime.now();
        LocalTime from = now.plusMinutes(15);
        LocalTime to = now.plusMinutes(30);
        LocalDate today = LocalDate.now();

        List<ReminderTarget> targets = notificationTargetQueryPort.findReminderTargets(from, to, today);
        if (targets.isEmpty()) return;

        int successCount = 0;
        List<String> failedUserIds = new ArrayList<>();

        for (ReminderTarget target : targets) {
            try {
                NotificationMessage msg = NotificationMessageTemplate.reminder(target.crewName());

                transactionTemplate.executeWithoutResult(status -> {
                    Notification notification = Notification.create(
                            target.userId(), NotificationType.REMINDER,
                            msg.title(), msg.content(),
                            NotificationTargetType.CREW, target.crewId()
                    );
                    notificationRepositoryPort.save(notification);
                });

                if (target.fcmToken() != null) {
                    notificationSendPort.send(target.fcmToken(), msg.title(), msg.content(),
                            Map.of("type", "REMINDER", "crewId", target.crewId()));
                }
                successCount++;
            } catch (Exception e) {
                failedUserIds.add(target.userId());
                log.error("리마인더 발송 실패 [userId={}, crewId={}]: {}",
                        target.userId(), target.crewId(), e.getMessage(), e);
            }
        }

        log.info("리마인더 발송 완료: 전체={}, 성공={}, 실패={}{}",
                targets.size(), successCount, failedUserIds.size(),
                failedUserIds.isEmpty() ? "" : " | 실패 userId: " + String.join(", ", failedUserIds));
    }
}
