package com.triagain.support.application;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessingResult;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.common.scheduler.FailedItem;
import com.triagain.support.domain.vo.NotificationMessageTemplate;
import com.triagain.support.domain.vo.NotificationMessageTemplate.NotificationMessage;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.port.out.FcmTokenCleanupPort;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationSendPort;
import com.triagain.support.port.out.NotificationTargetQueryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort.ReminderTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final int CHUNK_SIZE = 1;

    private final NotificationTargetQueryPort notificationTargetQueryPort;
    private final NotificationRepositoryPort notificationRepositoryPort;
    private final NotificationSendPort notificationSendPort;
    private final FcmTokenCleanupPort fcmTokenCleanupPort;
    private final ChunkProcessor chunkProcessor;
    private final DeadLetterRepositoryPort deadLetterRepositoryPort;

    /** 인증 마감 임박 리마인더 — 매 15분, deadlineTime 15~30분 전 미인증 유저 대상 */
    @Scheduled(cron = "0 0/15 * * * *")
    public void sendReminders() {
        LocalTime now = LocalTime.now();
        LocalTime from = now.plusMinutes(15);
        LocalTime to = now.plusMinutes(30);
        LocalDate today = LocalDate.now();

        List<ReminderTarget> targets = notificationTargetQueryPort.findReminderTargets(from, to, today);
        if (targets.isEmpty()) return;

        ChunkProcessingResult<ReminderTarget> result = chunkProcessor.execute(targets, CHUNK_SIZE, target -> {
            NotificationMessage msg = NotificationMessageTemplate.reminder(target.crewName());
            Notification notification = Notification.create(
                    target.userId(), NotificationType.REMINDER,
                    msg.title(), msg.content(),
                    NotificationTargetType.CREW, target.crewId()
            );
            notificationRepositoryPort.save(notification);
        });

        for (FailedItem<ReminderTarget> failed : result.failedItems()) {
            deadLetterRepositoryPort.save(DeadLetter.of(
                    DeadLetterTaskType.REMINDER,
                    failed.item().userId(),
                    failed.errorMessage()
            ));
        }

        // FCM 발송은 트랜잭션 밖에서 처리 — DB 저장 성공 건만 대상
        sendFcmNotifications(targets, result);

        log.info("리마인더 발송 완료: 전체={}건, DB저장 성공={}건, 실패={}건",
                targets.size(), result.successCount(), result.failedCount());
    }

    /** FCM 발송 — DB 저장 성공 건에 대해 푸시 전송, 실패 토큰 정리 */
    private void sendFcmNotifications(List<ReminderTarget> targets, ChunkProcessingResult<ReminderTarget> result) {
        List<ReminderTarget> failedTargets = result.failedItems().stream()
                .map(FailedItem::item).toList();

        for (ReminderTarget target : targets) {
            if (failedTargets.contains(target) || target.fcmToken() == null) continue;
            try {
                NotificationMessage msg = NotificationMessageTemplate.reminder(target.crewName());
                boolean tokenValid = notificationSendPort.send(target.fcmToken(), msg.title(), msg.content(),
                        Map.of("type", "REMINDER", "crewId", target.crewId()));
                if (!tokenValid) {
                    fcmTokenCleanupPort.clearFcmToken(target.userId());
                }
            } catch (Exception e) {
                log.warn("리마인더 FCM 발송 실패 [userId={}]: {}", target.userId(), e.getMessage());
            }
        }
    }
}
