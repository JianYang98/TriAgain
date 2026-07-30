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
import com.triagain.support.port.out.NotificationTargetQueryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort.CrewFirstVerificationTarget;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import com.triagain.verification.port.out.VerificationNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Verification → Support 컨텍스트 간 알림 어댑터 — 챌린지 성공 및 첫 인증 알림 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationNotificationAdapter implements VerificationNotificationPort {

	private static final LocalTime NOTIFY_START = LocalTime.of(8, 0);
	private static final LocalTime NOTIFY_END = LocalTime.of(22, 0);

	private final CrewMembershipQueryUseCase crewMembershipQueryUseCase;
	private final NotificationRepositoryPort notificationRepositoryPort;
	private final NotificationSendPort notificationSendPort;
	private final FcmTokenCleanupPort fcmTokenCleanupPort;
	private final UserRepositoryPort userRepositoryPort;
	private final NotificationTargetQueryPort notificationTargetQueryPort;
	private final Clock clock;

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

	/** 크루 오늘 첫 인증 모닝콜 fan-out — 본인 제외 ACTIVE 멤버에게 인앱+FCM(best-effort) */
	@Override
	public void sendCrewFirstVerificationNotification(String firstUserId, String crewId, LocalDate targetDate) {
		LocalTime now = LocalTime.now(clock);
		if (now.isBefore(NOTIFY_START) || !now.isBefore(NOTIFY_END)) {
			return;
		}

		if (notificationRepositoryPort.existsCrewFirstVerificationOnDate(crewId, targetDate)) {
			return;
		}

		List<CrewFirstVerificationTarget> targets =
				notificationTargetQueryPort.findCrewFirstVerificationTargets(crewId, firstUserId);
		if (targets.isEmpty()) {
			return;
		}

		String nickname = userRepositoryPort.findById(firstUserId)
				.map(User::getNickname).orElse("크루원");

		// [future] 수신자 규모↑ 또는 발송 보장(retry) 필요 시 ChunkProcessor + DeadLetter로 승격
		for (CrewFirstVerificationTarget t : targets) {
			try {
				NotificationMessage msg =
						NotificationMessageTemplate.crewFirstVerification(t.crewName(), nickname);
				Notification notification = Notification.create(
						t.userId(), NotificationType.CREW_FIRST_VERIFICATION,
						msg.title(), msg.content(),
						NotificationTargetType.CREW, crewId);
				notificationRepositoryPort.save(notification);

				if (t.fcmToken() != null) {
					sendFcm(t, msg, crewId);
				}
			} catch (Exception e) {
				log.error("첫인증 알림 처리 실패 [userId={}, crewId={}]: {}",
						t.userId(), crewId, e.getMessage());
			}
		}
	}

	private void sendFcm(CrewFirstVerificationTarget t, NotificationMessage msg, String crewId) {
		try {
			boolean valid = notificationSendPort.send(
					t.fcmToken(), msg.title(), msg.content(),
					Map.of("type", "CREW_FIRST_VERIFICATION", "crewId", crewId));
			if (!valid) {
				fcmTokenCleanupPort.clearFcmToken(t.userId());
			}
		} catch (Exception e) {
			log.warn("첫인증 FCM 발송 실패 [userId={}]: {}", t.userId(), e.getMessage());
		}
	}
}