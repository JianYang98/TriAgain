package com.triagain.habit.api;

import java.time.LocalTime;

import com.triagain.habit.domain.vo.HabitVerificationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateHabitRequest(
		@NotBlank(message = "습관 이름은 필수입니다") @Size(max = 50) String name,
		@NotNull(message = "인증 방식은 필수입니다") HabitVerificationType verificationType,
		LocalTime deadlineTime
) {
}
