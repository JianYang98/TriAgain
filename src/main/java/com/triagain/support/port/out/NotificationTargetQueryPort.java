package com.triagain.support.port.out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface NotificationTargetQueryPort {

	/** 마감 임박 미인증자 조회 — deadlineTime이 윈도우 내인 크루의 오늘 미인증 멤버 */
	List<ReminderTarget> findReminderTargets(LocalTime deadlineFrom, LocalTime deadlineTo, LocalDate targetDate);

	/** 오늘 시작 크루의 전체 멤버 조회 — 크루 시작 알림용 */
	List<CrewStartTarget> findCrewStartTargets(LocalDate startDate);

	/** 첫 인증 모닝콜 수신자 조회 — c.status='ACTIVE' 크루의 crew_members 전원 − 첫인증자 */
	List<CrewFirstVerificationTarget> findCrewFirstVerificationTargets(String crewId, String excludeUserId);

	record ReminderTarget(String userId, String fcmToken, String crewId, String crewName) {}
	record CrewStartTarget(String userId, String fcmToken, String crewId, String crewName) {}
	record CrewFirstVerificationTarget(String userId, String fcmToken, String crewId, String crewName) {}
}
