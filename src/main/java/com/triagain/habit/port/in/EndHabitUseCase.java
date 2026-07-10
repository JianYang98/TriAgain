package com.triagain.habit.port.in;

import com.triagain.habit.api.HabitResponse;

public interface EndHabitUseCase {

	/** 습관 종료 — status=ENDED 전이(D10, 터미널), IN_PROGRESS 사이클이 있으면 같은 트랜잭션에서 fail() 처리 */
	HabitResponse endHabit(String habitId, String userId);
}
