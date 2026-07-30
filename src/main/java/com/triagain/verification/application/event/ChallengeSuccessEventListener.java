package com.triagain.verification.application.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.triagain.verification.port.out.VerificationNotificationPort;

import lombok.RequiredArgsConstructor;

/** 챌린지 성공 알림 리스너 — 트랜잭션 커밋 후 FCM 발송으로 DB 커넥션 점유 방지 */
@Component
@RequiredArgsConstructor
public class ChallengeSuccessEventListener {

	private final VerificationNotificationPort verificationNotificationPort;

	// TODO: 챌린지 성공 알림 임시 비활성화 — 유저 알림 on/off 설정 UI 구현 후 복원
	// @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	// public void handle(ChallengeSuccessEvent event) {
	//     verificationNotificationPort.sendChallengeSuccessNotification(
	//             event.userId(), event.crewId());
	// }
}
