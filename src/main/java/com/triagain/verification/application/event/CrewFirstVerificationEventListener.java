package com.triagain.verification.application.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.triagain.verification.port.out.VerificationNotificationPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 첫 인증 모닝콜 리스너 — 트랜잭션 커밋 후 비동기 fan-out (요청 스레드 분리) */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		prefix = "notification.crew-first-verification",
		name = "enabled", havingValue = "true", matchIfMissing = false)
public class CrewFirstVerificationEventListener {

	private final VerificationNotificationPort verificationNotificationPort;

	/** 첫 인증 모닝콜 — 커밋 후 비동기 발송 */
	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(CrewFirstVerificationEvent event) {
		verificationNotificationPort.sendCrewFirstVerificationNotification(
				event.firstUserId(), event.crewId(), event.targetDate());
	}
}
