package com.triagain.crew.infra;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.triagain.crew.port.out.NotificationPort;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationMessageTemplate;
import com.triagain.support.domain.vo.NotificationMessageTemplate.NotificationMessage;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.port.out.FcmTokenCleanupPort;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationSendPort;
import com.triagain.user.port.out.UserRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Crew → Support 컨텍스트 간 알림 어댑터 — 인앱 저장 + FCM best-effort 발송 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAdapter implements NotificationPort {

	private final NotificationRepositoryPort notificationRepositoryPort;
	private final NotificationSendPort notificationSendPort;
	private final FcmTokenCleanupPort fcmTokenCleanupPort;
	private final UserRepositoryPort userRepositoryPort;

	@Override
	public void sendChallengeFailedNotifications(List<ChallengeFailedInfo> infos) {
		for (ChallengeFailedInfo info : infos) {
			try {
				NotificationMessage msg = NotificationMessageTemplate.challengeFailed(info.crewName());
				Notification notification = Notification.create(
						info.userId(), NotificationType.CHALLENGE_FAILED,
						msg.title(), msg.content(),
						NotificationTargetType.CREW, info.crewId()
				);
				notificationRepositoryPort.save(notification);

				// FCM best-effort 발송
				userRepositoryPort.findById(info.userId()).ifPresent(user -> {
					if (user.getFcmToken() != null) {
						try {
							boolean tokenValid = notificationSendPort.send(
									user.getFcmToken(), msg.title(), msg.content(),
									Map.of("type", "CHALLENGE_FAILED", "crewId", info.crewId()));
							if (!tokenValid) {
								fcmTokenCleanupPort.clearFcmToken(info.userId());
							}
						} catch (Exception e) {
							log.warn("챌린지 실패 FCM 발송 실패 [userId={}]: {}", info.userId(), e.getMessage());
						}
					}
				});
			} catch (Exception e) {
				log.error("챌린지 실패 알림 저장 실패 [userId={}, crewId={}]: {}",
						info.userId(), info.crewId(), e.getMessage());
			}
		}
	}
}
