package com.triagain.crew.port.out;

import java.util.List;

/** Crew → Support 컨텍스트 간 알림 발송 포트 — 챌린지 실패 알림에 사용 */
public interface NotificationPort {

	void sendChallengeFailedNotifications(List<ChallengeFailedInfo> infos);

	record ChallengeFailedInfo(String userId, String crewId, String crewName) {}
}