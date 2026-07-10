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
import com.triagain.habit.port.out.HabitRepositoryPort;

@ExtendWith(MockitoExtension.class)
class ResumeHabitServiceTest {

	private static final String USER_ID = "user-1";
	private static final String HABIT_ID = "habit-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	private ResumeHabitService service;

	@BeforeEach
	void setUp() {
		service = new ResumeHabitService(habitRepositoryPort);
	}

	@Test
	@DisplayName("PAUSED → ACTIVE로 전환된다")
	void pausedToActive() {
		// Given
		Habit habit = habitWithStatus(HabitStatus.PAUSED);
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.resumeHabit(HABIT_ID, USER_ID);

		// Then
		assertThat(result.status()).isEqualTo(HabitStatus.ACTIVE);
	}

	@Test
	@DisplayName("이미 ACTIVE면 no-op으로 200 반환한다")
	void alreadyActive_noop() {
		// Given
		Habit habit = habitWithStatus(HabitStatus.ACTIVE);
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.resumeHabit(HABIT_ID, USER_ID);

		// Then
		assertThat(result.status()).isEqualTo(HabitStatus.ACTIVE);
	}

	@Test
	@DisplayName("ENDED 습관 재개 시도 — HABIT_NOT_FOUND(HB001, 터미널)")
	void endedHabit_throws() {
		// Given
		Habit ended = Habit.of(HABIT_ID, USER_ID, "습관", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ENDED, LocalDateTime.now().minusDays(5), LocalDateTime.now());
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(ended));

		// When & Then
		assertThatThrownBy(() -> service.resumeHabit(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_FOUND);
	}

	private Habit habitWithStatus(HabitStatus status) {
		return Habit.of(HABIT_ID, USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), status, LocalDateTime.now().minusDays(1), null);
	}
}
