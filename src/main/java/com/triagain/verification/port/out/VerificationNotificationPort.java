package com.triagain.verification.port.out;

import java.time.LocalDate;

/** Verification → Support 컨텍스트 간 알림 포트 — 챌린지 성공 및 첫 인증 알림에 사용 */
public interface VerificationNotificationPort {

	void sendChallengeSuccessNotification(String userId, String crewId);

	/** 크루 오늘 첫 인증 모닝콜 fan-out 발송 */
	void sendCrewFirstVerificationNotification(String firstUserId, String crewId, LocalDate targetDate);
}