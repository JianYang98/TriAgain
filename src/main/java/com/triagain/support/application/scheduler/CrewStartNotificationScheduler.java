package com.triagain.support.application.scheduler;

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
import com.triagain.support.port.out.NotificationTargetQueryPort.CrewStartTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrewStartNotificationScheduler {

    private static final int CHUNK_SIZE = 1;

    private final NotificationTargetQueryPort notificationTargetQueryPort;
    private final NotificationRepositoryPort notificationRepositoryPort;
    private final NotificationSendPort notificationSendPort;
    private final FcmTokenCleanupPort fcmTokenCleanupPort;
    private final ChunkProcessor chunkProcessor;
    private final DeadLetterRepositoryPort deadLetterRepositoryPort;

    /** 크루 시작 알림 — 매일 09:00, 오늘 시작된 크루의 전체 멤버에게 알림 */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendCrewStartNotifications() {
        List<CrewStartTarget> targets = notificationTargetQueryPort.findCrewStartTargets(LocalDate.now());
        if (targets.isEmpty()) return;

        ChunkProcessingResult<CrewStartTarget> result = chunkProcessor.execute(targets, CHUNK_SIZE, target -> {
            NotificationMessage msg = NotificationMessageTemplate.crewStarted(target.crewName());
            Notification notification = Notification.create(
                    target.userId(), NotificationType.CREW_STARTED,
                    msg.title(), msg.content(),
                    NotificationTargetType.CREW, target.crewId()
            );
            notificationRepositoryPort.save(notification);
        });

        for (FailedItem<CrewStartTarget> failed : result.failedItems()) {
            deadLetterRepositoryPort.save(DeadLetter.of(
                    DeadLetterTaskType.CREW_START_NOTIFICATION,
                    failed.item().userId(),
                    failed.errorMessage()
            ));
        }

        // FCM 발송은 트랜잭션 밖에서 처리 — DB 저장 성공 건만 대상
        sendFcmNotifications(targets, result);

        log.info("크루 시작 알림 발송 완료: 전체={}건, DB저장 성공={}건, 실패={}건",
                targets.size(), result.successCount(), result.failedCount());
    }

    /** FCM 발송 — DB 저장 성공 건에 대해 푸시 전송, 실패 토큰 정리 */
    private void sendFcmNotifications(List<CrewStartTarget> targets, ChunkProcessingResult<CrewStartTarget> result) {
        List<CrewStartTarget> failedTargets = result.failedItems().stream()
                .map(FailedItem::item).toList();

        for (CrewStartTarget target : targets) {
            if (failedTargets.contains(target) || target.fcmToken() == null) continue;
            try {
                NotificationMessage msg = NotificationMessageTemplate.crewStarted(target.crewName());
                boolean tokenValid = notificationSendPort.send(target.fcmToken(), msg.title(), msg.content(),
                        Map.of("type", "CREW_STARTED", "crewId", target.crewId()));
                if (!tokenValid) {
                    fcmTokenCleanupPort.clearFcmToken(target.userId());
                }
            } catch (Exception e) {
                log.warn("크루 시작 FCM 발송 실패 [userId={}]: {}", target.userId(), e.getMessage());
            }
        }
    }
}
