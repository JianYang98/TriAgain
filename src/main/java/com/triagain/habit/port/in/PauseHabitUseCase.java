package com.triagain.habit.port.in;

import com.triagain.habit.api.HabitResponse;

public interface PauseHabitUseCase {

	/** 습관 멈춤 — IN_PROGRESS 사이클이 없을 때만 가능(HB004), 알림 없음·기록 보존·재개 가능 */
	HabitResponse pauseHabit(String habitId, String userId);
}
