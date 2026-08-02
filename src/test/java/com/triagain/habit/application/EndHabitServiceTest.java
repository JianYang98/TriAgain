package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
class EndHabitServiceTest {

	private static final String USER_ID = "user-1";
	private static final String HABIT_ID = "habit-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	private EndHabitService service;

	@BeforeEach
	void setUp() {
		service = new EndHabitService(habitRepositoryPort, habitCycleRepositoryPort);
	}

	@Test
	@DisplayName("IN_PROGRESS 사이클이 없으면 습관만 ENDED로 전환된다")
	void noInProgressCycle_endsHabitOnly() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.endHabit(HABIT_ID, USER_ID);

		// Then
		assertThat(result.status()).isEqualTo(HabitStatus.ENDED);
		assertThat(result.endedAt()).isNotNull();
		verify(habitCycleRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("IN_PROGRESS 사이클이 있으면 같은 트랜잭션에서 fail() 처리된다")
	void inProgressCycleExists_failsInSameTransaction() {
		// Given
		Habit habit = activeHabit();
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		HabitCycle cycle = HabitCycle.of("HCYC-1", HABIT_ID, USER_ID, 1, 3, 1,
				HabitCycleStatus.IN_PROGRESS, LocalDate.now(), LocalDateTime.now().plusDays(1), LocalDateTime.now());
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		service.endHabit(HABIT_ID, USER_ID);

		// Then
		assertThat(cycle.getStatus()).isEqualTo(HabitCycleStatus.FAILED);
		verify(habitCycleRepositoryPort).save(cycle);
	}

	@Test
	@DisplayName("이미 ENDED인 습관 재종료 시도 — HABIT_NOT_FOUND(HB001)")
	void alreadyEnded_throws() {
		// Given
		Habit ended = Habit.of(HABIT_ID, USER_ID, "습관", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ENDED, LocalDateTime.now().minusDays(5), LocalDateTime.now(),
						null);
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(ended));

		// When & Then
		assertThatThrownBy(() -> service.endHabit(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_FOUND);
	}

	private Habit activeHabit() {
		return Habit.of(HABIT_ID, USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ACTIVE, LocalDateTime.now().minusDays(1), null, null);
	}
}
