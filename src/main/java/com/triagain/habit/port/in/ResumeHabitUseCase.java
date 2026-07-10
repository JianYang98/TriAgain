package com.triagain.habit.port.in;

import com.triagain.habit.api.HabitResponse;

public interface ResumeHabitUseCase {

	/** 습관 재개 — PAUSED → ACTIVE·사이클없음, ACTIVE 상태에서 호출 시 no-op 200 */
	HabitResponse resumeHabit(String habitId, String userId);
}
