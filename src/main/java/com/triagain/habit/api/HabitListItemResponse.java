package com.triagain.habit.api;

import java.time.LocalTime;

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;

/** 내 습관 목록 아이템 — 홈 탭(오늘 할 일/오늘 완료/예정)의 솔로 데이터 소스 (step2 §2). activeCycle은 IN_PROGRESS 사이클 없으면 null */
public record HabitListItemResponse(
		String habitId,
		String name,
		HabitVerificationType verificationType,
		String verificationContent,
		LocalTime deadlineTime,
		HabitStatus status,
		int successCount,
		boolean todayVerified,
		HabitCycleResponse activeCycle
) {

	/** 습관 도메인 모델 + 집계값(성공횟수·오늘인증여부·활성사이클) → 홈 목록 아이템 응답 변환 */
	public static HabitListItemResponse from(
			Habit habit, int successCount, boolean todayVerified, HabitCycle activeCycle) {
		return new HabitListItemResponse(
				habit.getId(),
				habit.getName(),
				habit.getVerificationType(),
				habit.getVerificationContent(),
				habit.getDeadlineTime(),
				habit.getStatus(),
				successCount,
				todayVerified,
				activeCycle != null ? HabitCycleResponse.from(activeCycle) : null
		);
	}
}
