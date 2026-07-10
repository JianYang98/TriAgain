package com.triagain.habit.api;

public record CreateHabitVerificationRequest(
		Long uploadSessionId,
		String textContent
) {
}
