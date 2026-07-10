package com.triagain.habit.port.in;

import com.triagain.habit.api.HabitResponse;

public interface UpdateHabitNameUseCase {

	/** 습관 이름 수정 — name만 변경 가능(v1), verificationType/deadlineTime 변경 불가 */
	HabitResponse updateHabitName(UpdateHabitNameCommand command);

	record UpdateHabitNameCommand(String userId, String habitId, String name) {
	}
}
