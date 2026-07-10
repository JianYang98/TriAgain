package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

@ExtendWith(MockitoExtension.class)
class CancelHabitCycleServiceTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 5, 14, 0, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW.atZone(ZONE).toInstant(), ZONE);
	private static final LocalDate TODAY = FIXED_NOW.toLocalDate();

	private static final String USER_ID = "user-1";
	private static final String HABIT_ID = "habit-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	private CancelHabitCycleService service;

	@BeforeEach
	void setUp() {
		service = new CancelHabitCycleService(habitRepositoryPort, habitCycleRepositoryPort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("시작 전(TOMORROW) 사이클 취소 — hard delete된다")
	void beforeStartDate_deletesCycle() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		HabitCycle cycle = HabitCycle.of("HCYC-1", HABIT_ID, USER_ID, 1, 3, 0,
				HabitCycleStatus.IN_PROGRESS, TODAY.plusDays(1), FIXED_NOW.plusDays(4), FIXED_NOW);
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));

		// When
		assertThatCode(() -> service.cancelCurrentCycle(HABIT_ID, USER_ID)).doesNotThrowAnyException();

		// Then
		verify(habitCycleRepositoryPort).deleteById("HCYC-1");
	}

	@Test
	@DisplayName("시작일 도래 후 취소 시도 — HABIT_CYCLE_CANCEL_NOT_ALLOWED(HB007)")
	void afterStartDate_throws() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		HabitCycle cycle = HabitCycle.of("HCYC-1", HABIT_ID, USER_ID, 1, 3, 0,
				HabitCycleStatus.IN_PROGRESS, TODAY, FIXED_NOW.plusDays(3), FIXED_NOW);
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));

		// When & Then
		assertThatThrownBy(() -> service.cancelCurrentCycle(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_CYCLE_CANCEL_NOT_ALLOWED);
	}

	@Test
	@DisplayName("활성 사이클 없음 — HABIT_CYCLE_NOT_IN_PROGRESS(HB003)")
	void noActiveCycle_throws() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> service.cancelCurrentCycle(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
	}

	private Habit activeHabit() {
		return Habit.of(HABIT_ID, USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ACTIVE, FIXED_NOW.minusDays(1), null);
	}
}
