package com.triagain.support.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.port.out.FcmTokenCleanupPort;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationSendPort;
import com.triagain.support.port.out.NotificationTargetQueryPort;
import com.triagain.support.port.out.NotificationTargetQueryPort.CrewStartTarget;

@ExtendWith(MockitoExtension.class)
class CrewStartNotificationSchedulerTest {

	@Mock
	private NotificationTargetQueryPort notificationTargetQueryPort;

	@Mock
	private NotificationRepositoryPort notificationRepositoryPort;

	@Mock
	private NotificationSendPort notificationSendPort;

	@Mock
	private FcmTokenCleanupPort fcmTokenCleanupPort;

	@Mock
	private DeadLetterRepositoryPort deadLetterRepositoryPort;

	private CrewStartNotificationScheduler crewStartNotificationScheduler;

	@BeforeEach
	void setUp() {
		TransactionTemplate transactionTemplate = new TransactionTemplate();
		transactionTemplate.setTransactionManager(new AbstractPlatformTransactionManager() {
			@Override
			protected Object doGetTransaction() {
				return new Object();
			}

			@Override
			protected void doBegin(Object transaction, TransactionDefinition definition) {
			}

			@Override
			protected void doCommit(DefaultTransactionStatus status) {
			}

			@Override
			protected void doRollback(DefaultTransactionStatus status) {
			}
		});
		ChunkProcessor chunkProcessor = new ChunkProcessor(transactionTemplate);

		crewStartNotificationScheduler = new CrewStartNotificationScheduler(
				notificationTargetQueryPort, notificationRepositoryPort,
				notificationSendPort, fcmTokenCleanupPort, chunkProcessor, deadLetterRepositoryPort);
	}

	@DisplayName("크루 시작 타겟이 2명이면 알림 저장 2회 + 푸시 발송 2회 호출된다")
	@Test
	void notifyCrewStart_withTargets_savesAndSends() {
		// given
		List<CrewStartTarget> targets = List.of(
				new CrewStartTarget("user-1", "token-1", "crew-1", "독서 모임"),
				new CrewStartTarget("user-2", "token-2", "crew-1", "독서 모임")
		);
		given(notificationTargetQueryPort.findCrewStartTargets(any(LocalDate.class)))
				.willReturn(targets);
		given(notificationSendPort.send(anyString(), anyString(), anyString(), anyMap()))
				.willReturn(true);

		// when
		crewStartNotificationScheduler.sendCrewStartNotifications();

		// then
		verify(notificationRepositoryPort, times(2)).save(any(Notification.class));
		verify(notificationSendPort, times(2)).send(anyString(), anyString(), anyString(), anyMap());
		verify(fcmTokenCleanupPort, never()).clearFcmToken(anyString());
	}

	@DisplayName("fcmToken이 null이면 알림은 저장하되 푸시는 발송하지 않는다")
	@Test
	void notifyCrewStart_nullFcmToken_savesButDoesNotSend() {
		// given
		List<CrewStartTarget> targets = List.of(
				new CrewStartTarget("user-1", null, "crew-1", "독서 모임")
		);
		given(notificationTargetQueryPort.findCrewStartTargets(any(LocalDate.class)))
				.willReturn(targets);

		// when
		crewStartNotificationScheduler.sendCrewStartNotifications();

		// then
		verify(notificationRepositoryPort).save(any(Notification.class));
		verify(notificationSendPort, never()).send(anyString(), anyString(), anyString(), anyMap());
	}

	@DisplayName("타겟이 없으면 아무 작업도 수행하지 않는다")
	@Test
	void notifyCrewStart_noTargets_doesNothing() {
		// given
		given(notificationTargetQueryPort.findCrewStartTargets(any(LocalDate.class)))
				.willReturn(Collections.emptyList());

		// when
		crewStartNotificationScheduler.sendCrewStartNotifications();

		// then
		verify(notificationRepositoryPort, never()).save(any(Notification.class));
		verify(notificationSendPort, never()).send(anyString(), anyString(), anyString(), anyMap());
	}

	@DisplayName("FCM 토큰이 무효하면 토큰 정리 포트가 호출된다")
	@Test
	void notifyCrewStart_invalidToken_clearsToken() {
		// given
		List<CrewStartTarget> targets = List.of(
				new CrewStartTarget("user-1", "token-1", "crew-1", "독서 모임")
		);
		given(notificationTargetQueryPort.findCrewStartTargets(any(LocalDate.class)))
				.willReturn(targets);
		given(notificationSendPort.send(anyString(), anyString(), anyString(), anyMap()))
				.willReturn(false);

		// when
		crewStartNotificationScheduler.sendCrewStartNotifications();

		// then
		verify(notificationRepositoryPort).save(any(Notification.class));
		verify(fcmTokenCleanupPort).clearFcmToken("user-1");
	}

	@DisplayName("개별 타겟 DB 저장 실패 시 DeadLetter에 기록되고 나머지는 정상 처리된다")
	@Test
	void notifyCrewStart_individualFailure_recordsDeadLetterAndContinues() {
		// given
		List<CrewStartTarget> targets = List.of(
				new CrewStartTarget("user-1", "token-1", "crew-1", "독서 모임"),
				new CrewStartTarget("user-2", "token-2", "crew-1", "독서 모임")
		);
		given(notificationTargetQueryPort.findCrewStartTargets(any(LocalDate.class)))
				.willReturn(targets);
		given(notificationSendPort.send(anyString(), anyString(), anyString(), anyMap()))
				.willReturn(true);

		// 첫 번째 아이템: 청크 시도 + 건별 재시도 모두 실패 (2회 throw), 두 번째 아이템: 성공
		doThrow(new RuntimeException("DB 오류"))
				.doThrow(new RuntimeException("DB 오류"))
				.doAnswer(invocation -> invocation.getArgument(0))
				.when(notificationRepositoryPort).save(any(Notification.class));

		// when
		crewStartNotificationScheduler.sendCrewStartNotifications();

		// then — 첫 번째: 청크(1회) + 건별재시도(1회) = 2회 실패, 두 번째: 청크(1회) 성공 → 총 3회 호출
		verify(notificationRepositoryPort, times(3)).save(any(Notification.class));
		verify(deadLetterRepositoryPort).save(any(DeadLetter.class));
		verify(notificationSendPort).send(eq("token-2"), anyString(), anyString(), anyMap());
	}
}
