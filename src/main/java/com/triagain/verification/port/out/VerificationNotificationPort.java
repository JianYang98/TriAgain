package com.triagain.verification.port.out;

/** Verification → Support 컨텍스트 간 알림 포트 — 챌린지 성공 알림에 사용 */
public interface VerificationNotificationPort {

    void sendChallengeSuccessNotification(String userId, String crewId);
}