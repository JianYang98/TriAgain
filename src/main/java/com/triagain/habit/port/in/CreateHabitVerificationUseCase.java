package com.triagain.habit.port.in;

import com.triagain.habit.api.HabitVerificationResponse;

public interface CreateHabitVerificationUseCase {

	/** 솔로 인증 생성 — completedDays+1, targetDays 도달 시 사이클 SUCCESS 전환(같은 트랜잭션) */
	HabitVerificationResponse createVerification(CreateHabitVerificationCommand command);

	record CreateHabitVerificationCommand(
			String userId,
			String habitId,
			Long uploadSessionId,
			String textContent
	) {
	}
}
