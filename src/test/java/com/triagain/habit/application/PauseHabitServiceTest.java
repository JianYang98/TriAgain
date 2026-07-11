package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
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
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

@ExtendWith(MockitoExtension.class)
class PauseHabitServiceTest {

	private static final String USER_ID = "user-1";
	private static final String HABIT_ID = "habit-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	private PauseHabitService service;

	@BeforeEach
	void setUp() {
		service = new PauseHabitService(habitRepositoryPort, habitCycleRepositoryPort);
	}

	@Test
	@DisplayName("IN_PROGRESS 사이클 없으면 PAUSED로 전환된다")
	void noInProgressCycle_pauses() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.pauseHabit(HABIT_ID, USER_ID);

		// Then
		assertThat(result.status()).isEqualTo(HabitStatus.PAUSED);
	}

	@Test
	@DisplayName("IN_PROGRESS 사이클 존재 시 HABIT_PAUSE_NOT_ALLOWED(HB004) 예외가 발생한다")
	void inProgressCycleExists_throws() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		HabitCycle cycle = HabitCycle.of("HCYC-1", HABIT_ID, USER_ID, 1, 3, 0,
				HabitCycleStatus.IN_PROGRESS, LocalDate.now(), LocalDateTime.now().plusDays(1), LocalDateTime.now());
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));

		// When & Then
		assertThatThrownBy(() -> service.pauseHabit(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_PAUSE_NOT_ALLOWED);
	}

	@Test
	@DisplayName("ENDED 습관 — HABIT_NOT_FOUND(HB001) 예외가 발생한다")
	void endedHabit_throws() {
		// Given
		Habit ended = Habit.of(HABIT_ID, USER_ID, "습관", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ENDED, LocalDateTime.now().minusDays(5), LocalDateTime.now(), null);
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(ended));

		// When & Then
		assertThatThrownBy(() -> service.pauseHabit(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_FOUND);
	}

	@Test
	@DisplayName("타인 습관 — HABIT_ACCESS_DENIED(HB005) 예외가 발생한다")
	void notOwner_throws() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));

		// When & Then
		assertThatThrownBy(() -> service.pauseHabit(HABIT_ID, "other-user"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_ACCESS_DENIED);
	}

	private Habit activeHabit() {
		return Habit.of(HABIT_ID, USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ACTIVE, LocalDateTime.now().minusDays(1), null, null);
	}
}
