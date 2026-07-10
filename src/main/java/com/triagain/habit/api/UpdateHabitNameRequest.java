package com.triagain.habit.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateHabitNameRequest(
		@NotBlank(message = "습관 이름은 필수입니다") @Size(max = 50) String name
) {
}
