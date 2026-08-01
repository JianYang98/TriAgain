package com.triagain.verification.infra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.crew.port.in.CrewMembershipQueryUseCase;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.port.out.FcmTokenCleanupPort;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationSendPort;
import com.triagain.support.port.out.NotificationTargetQueryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort.CrewFirstVerificationTarget;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;

/**
 * T2~T7: VerificationNotificationAdapter.sendCrewFirstVerificationNotification 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class VerificationNotificationAdapterCrewFirstTest {

	@Mock
	private CrewMembershipQueryUseCase crewMembershipQueryUseCase;
	@Mock
	private NotificationRepositoryPort notificationRepositoryPort;
	@Mock
	private NotificationSendPort notificationSendPort;
	@Mock
	private FcmTokenCleanupPort fcmTokenCleanupPort;
	@Mock
	private UserRepositoryPort userRepositoryPort;
	@Mock
	private NotificationTargetQueryPort notificationTargetQueryPort;

	private VerificationNotificationAdapter adapter;

	private static final String FIRST_USER_ID = "user-first";
	private static final String OTHER_USER_ID = "user-other";
	private static final String CREW_ID = "crew-1";
	private static final LocalDate TODAY = LocalDate.now();

	/** 08:00~22:00 사이 고정 Clock — quiet hours 안쪽 */
	private static Clock clockAt(int hour, int minute) {
		String timeStr = String.format("%02d:%02d:00", hour, minute);
		Instant instant = Instant.parse("2026-06-17T" + timeStr + "Z");
		// ZoneOffset.UTC로 고정하면 LocalTime.now(clock) = hour:minute
		return Clock.fixed(instant, ZoneId.of("UTC"));
	}

	@BeforeEach
	void setUp() {
		// 기본 Clock = 10:00 (quiet hours 안쪽)
		adapter = new VerificationNotificationAdapter(
				crewMembershipQueryUseCase, notificationRepositoryPort,
				notificationSendPort, fcmTokenCleanupPort,
				userRepositoryPort, notificationTargetQueryPort,
				clockAt(10, 0));
	}

	private User makeUser(String userId, String nickname, String fcmToken) {
		return User.of(userId, "KAKAO", userId + "@test.com",
				nickname, null, fcmToken, null,
				LocalDateTime.now(), LocalDateTime.now(), null, 0);
	}

	@Test
	@DisplayName("T2: 수신자 목록에 첫인증자 본인은 포함되지 않는다 — excludeUserId 파라미터 검증")
	void sendCrewFirstVerification_excludesFirstUser() {
		// Given
		given(notificationRepositoryPort.existsCrewFirstVerificationOnDate(CREW_ID, TODAY))
				.willReturn(false);
		// adapter는 excludeUserId=FIRST_USER_ID로 findCrewFirstVerificationTargets 호출
		given(notificationTargetQueryPort.findCrewFirstVerificationTargets(CREW_ID, FIRST_USER_ID))
				.willReturn(List.of(
						new CrewFirstVerificationTarget(OTHER_USER_ID, "fcm-other", CREW_ID, "크루명")
				));
		given(userRepositoryPort.findById(FIRST_USER_ID))
				.willReturn(Optional.of(makeUser(FIRST_USER_ID, "첫인증자", null)));
		given(notificationRepositoryPort.save(any(Notification.class)))
				.willAnswer(inv -> inv.getArgument(0));
		given(notificationSendPort.send(anyString(), anyString(), anyString(), anyMap()))
				.willReturn(true);

		// When
		adapter.sendCrewFirstVerificationNotification(FIRST_USER_ID, CREW_ID, TODAY);

		// Then — OTHER_USER_ID에게만 알림 저장
		verify(notificationRepositoryPort, times(1)).save(any(Notification.class));
	}

	@Test
	@DisplayName("T3: 멱등 — existsCrewFirstVerificationOnDate가 true이면 fan-out 전체 skip")
	void sendCrewFirstVerification_idempotentSkip_whenAlreadySent() {
		// Given
		given(notificationRepositoryPort.existsCrewFirstVerificationOnDate(CREW_ID, TODAY))
				.willReturn(true);

		// When
		adapter.sendCrewFirstVerificationNotification(FIRST_USER_ID, CREW_ID, TODAY);

		// Then
		verify(notificationTargetQueryPort, never()).findCrewFirstVerificationTargets(anyString(), anyString());
		verify(notificationRepositoryPort, never()).save(any(Notification.class));
	}

	@Test
	@DisplayName("T4 (G1): FCM send()가 예외를 던져도 인앱 알림 save는 유지된다 — NotificationSendPort mock stub")
	void sendCrewFirstVerification_fcmFailure_inAppSaved() {
		// Given
		given(notificationRepositoryPort.existsCrewFirstVerificationOnDate(CREW_ID, TODAY))
				.willReturn(false);
		given(notificationTargetQueryPort.findCrewFirstVerificationTargets(CREW_ID, FIRST_USER_ID))
				.willReturn(List.of(
						new CrewFirstVerificationTarget(OTHER_USER_ID, "fcm-token", CREW_ID, "크루명")
				));
		given(userRepositoryPort.findById(FIRST_USER_ID))
				.willReturn(Optional.of(makeUser(FIRST_USER_ID, "닉네임", null)));
		given(notificationRepositoryPort.save(any(Notification.class)))
				.willAnswer(inv -> inv.getArgument(0));
		// G1: environment가 아닌 명시적 stub으로 FCM 실패를 주입
		willThrow(new RuntimeException("FCM 서버 에러"))
				.given(notificationSendPort).send(anyString(), anyString(), anyString(), anyMap());

		// When — 예외가 전파되지 않아야 함
		adapter.sendCrewFirstVerificationNotification(FIRST_USER_ID, CREW_ID, TODAY);

		// Then — 인앱 알림은 저장됨
		verify(notificationRepositoryPort).save(any(Notification.class));
	}

	@Test
	@DisplayName("T5: quiet hours 밖(07:59) 트리거 → 발송 0건")
	void sendCrewFirstVerification_beforeQuietHoursStart_skips() {
		// Given — 07:59 clock
		adapter = new VerificationNotificationAdapter(
				crewMembershipQueryUseCase, notificationRepositoryPort,
				notificationSendPort, fcmTokenCleanupPort,
				userRepositoryPort, notificationTargetQueryPort,
				clockAt(7, 59));

		// When
		adapter.sendCrewFirstVerificationNotification(FIRST_USER_ID, CREW_ID, TODAY);

		// Then
		verify(notificationRepositoryPort, never()).save(any(Notification.class));
		verify(notificationTargetQueryPort, never()).findCrewFirstVerificationTargets(anyString(), anyString());
	}

	@Test
	@DisplayName("T5: quiet hours 밖(22:00) 트리거 → 발송 0건")
	void sendCrewFirstVerification_atQuietHoursEnd_skips() {
		// Given — 22:00 clock (22:00 미만이어야 통과이므로 22:00은 skip)
		adapter = new VerificationNotificationAdapter(
				crewMembershipQueryUseCase, notificationRepositoryPort,
				notificationSendPort, fcmTokenCleanupPort,
				userRepositoryPort, notificationTargetQueryPort,
				clockAt(22, 0));

		// When
		adapter.sendCrewFirstVerificationNotification(FIRST_USER_ID, CREW_ID, TODAY);

		// Then
		verify(notificationRepositoryPort, never()).save(any(Notification.class));
		verify(notificationTargetQueryPort, never()).findCrewFirstVerificationTargets(anyString(), anyString());
	}

	@Test
	@DisplayName("T7: solo 크루(수신자 0명) → 발송 0건, 예외 없음")
	void sendCrewFirstVerification_soloCrewNoRecipients_noSendNoException() {
		// Given
		given(notificationRepositoryPort.existsCrewFirstVerificationOnDate(CREW_ID, TODAY))
				.willReturn(false);
		given(notificationTargetQueryPort.findCrewFirstVerificationTargets(CREW_ID, FIRST_USER_ID))
				.willReturn(Collections.emptyList());

		// When — 예외 없이 정상 종료
		adapter.sendCrewFirstVerificationNotification(FIRST_USER_ID, CREW_ID, TODAY);

		// Then
		verify(notificationRepositoryPort, never()).save(any(Notification.class));
		verify(notificationSendPort, never()).send(anyString(), anyString(), anyString(), anyMap());
	}

	@Test
	@DisplayName("T4 추가: FCM send()가 false 반환 시 인앱 저장 유지 + 토큰 정리 호출")
	void sendCrewFirstVerification_fcmReturnsFalse_inAppSavedAndTokenCleared() {
		// Given
		given(notificationRepositoryPort.existsCrewFirstVerificationOnDate(CREW_ID, TODAY))
				.willReturn(false);
		given(notificationTargetQueryPort.findCrewFirstVerificationTargets(CREW_ID, FIRST_USER_ID))
				.willReturn(List.of(
						new CrewFirstVerificationTarget(OTHER_USER_ID, "fcm-token", CREW_ID, "크루명")
				));
		given(userRepositoryPort.findById(FIRST_USER_ID))
				.willReturn(Optional.of(makeUser(FIRST_USER_ID, "닉네임", null)));
		given(notificationRepositoryPort.save(any(Notification.class)))
				.willAnswer(inv -> inv.getArgument(0));
		// G1: send()가 false 반환
		given(notificationSendPort.send(anyString(), anyString(), anyString(), anyMap()))
				.willReturn(false);

		// When
		adapter.sendCrewFirstVerificationNotification(FIRST_USER_ID, CREW_ID, TODAY);

		// Then — 인앱 저장 유지 + 만료 토큰 정리
		verify(notificationRepositoryPort).save(any(Notification.class));
		verify(fcmTokenCleanupPort).clearFcmToken(OTHER_USER_ID);
	}
}
