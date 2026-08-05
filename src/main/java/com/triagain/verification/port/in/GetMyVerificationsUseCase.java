package com.triagain.verification.port.in;

import java.time.LocalDate;
import java.util.List;

public interface GetMyVerificationsUseCase {

	/** 내 인증 현황 조회 — 캘린더/스트릭/달성 횟수 */
	MyVerificationsResult getMyVerifications(String crewId, String userId);

	record MyProgress(String challengeId, String status, int completedDays, int targetDays) {
	}

	record MyVerificationsResult(
			List<LocalDate> verifiedDates,
			int streakCount,
			int completedChallenges,
			MyProgress myProgress,
			TodaySlot todaySlot
	) {
	}

	/**
	 * 오늘 슬롯 인증 현황 — FE가 수정/취소 가능 여부·잔여 횟수를 판단하는 데 사용.
	 * null이면 오늘 인증 없음. textContent·imageUrl은 수정 다이얼로그 프리필용
	 * (TEXT/PHOTO 인증 종류에 따라 한쪽은 null)
	 */
	record TodaySlot(String verificationId, int slotAttempt, String textContent, String imageUrl) {
	}
}
