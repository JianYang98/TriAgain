package com.triagain.habit.port.in;

import java.time.LocalTime;

import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.vo.HabitVerificationType;

public interface CreateHabitUseCase {

	/** 습관 등록 — 사이클은 생성하지 않음(D3), 등록 직후 별도 API로 사이클 시작 필요 */
	HabitResponse createHabit(CreateHabitCommand command);

	record CreateHabitCommand(
			String userId,
			String name,
			HabitVerificationType verificationType,
			LocalTime deadlineTime
	) {
	}
}
