package com.triagain.verification.application.event;

/** 챌린지 3일 연속 성공 이벤트 — 트랜잭션 커밋 후 알림 발송에 사용 */
public record ChallengeSuccessEvent(String userId, String crewId) {
}
