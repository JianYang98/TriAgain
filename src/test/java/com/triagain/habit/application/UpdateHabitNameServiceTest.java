package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.in.UpdateHabitNameUseCase.UpdateHabitNameCommand;
import com.triagain.habit.port.out.HabitRepositoryPort;

@ExtendWith(MockitoExtension.class)
class UpdateHabitNameServiceTest {

	private static final String USER_ID = "user-1";
	private static final String HABIT_ID = "habit-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	private UpdateHabitNameService service;

	@BeforeEach
	void setUp() {
		service = new UpdateHabitNameService(habitRepositoryPort);
	}

	@Test
	@DisplayName("이름 수정 성공")
	void success() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.updateHabitName(new UpdateHabitNameCommand(USER_ID, HABIT_ID, "새 이름"));

		// Then
		assertThat(result.name()).isEqualTo("새 이름");
	}

	@Test
	@DisplayName("타인 습관 수정 시도 — HABIT_ACCESS_DENIED(HB005)")
	void notOwner_throws() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));

		// When & Then
		assertThatThrownBy(() -> service.updateHabitName(
				new UpdateHabitNameCommand("other-user", HABIT_ID, "새 이름")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_ACCESS_DENIED);
	}

	@Test
	@DisplayName("ENDED 습관 수정 시도 — HABIT_NOT_FOUND(HB001)")
	void endedHabit_throws() {
		// Given
		Habit ended = Habit.of(HABIT_ID, USER_ID, "습관", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ENDED, LocalDateTime.now().minusDays(5), LocalDateTime.now(),
						null);
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(ended));

		// When & Then
		assertThatThrownBy(() -> service.updateHabitName(new UpdateHabitNameCommand(USER_ID, HABIT_ID, "새 이름")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_FOUND);
	}

	private Habit activeHabit() {
		return Habit.of(HABIT_ID, USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ACTIVE, LocalDateTime.now().minusDays(1), null, null);
	}
}
