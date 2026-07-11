package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
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

/** verification BC의 HabitPort가 위임하는 업로드 세션 발급 가능 여부 검증 — crew validateCrewAndDeadline 대칭(step2 §9) */
@ExtendWith(MockitoExtension.class)
class ValidateHabitUploadAccessServiceTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 5, 14, 0, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW.atZone(ZONE).toInstant(), ZONE);

	private static final String USER_ID = "user-1";
	private static final String HABIT_ID = "habit-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	private ValidateHabitUploadAccessService service;

	@BeforeEach
	void setUp() {
		service = new ValidateHabitUploadAccessService(habitRepositoryPort, habitCycleRepositoryPort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("PHOTO 습관·ACTIVE·활성 사이클 없음·마감 전 — 통과")
	void noActiveCycle_beforeTodayDeadline_passes() {
		// Given
		Habit habit = photoHabit(HabitStatus.ACTIVE, LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());

		// When & Then
		assertThatCode(() -> service.validateHabitUploadAccess(HABIT_ID, USER_ID)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("활성 사이클이 있으면 cycle.deadline 기준으로 마감을 검증한다")
	void activeCycle_usesCycleDeadline() {
		// Given
		Habit habit = photoHabit(HabitStatus.ACTIVE, LocalTime.of(0, 0, 0));
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));
		HabitCycle cycle = HabitCycle.of("HCYC-1", HABIT_ID, USER_ID, 1, 3, 0,
				HabitCycleStatus.IN_PROGRESS, FIXED_NOW.toLocalDate(), FIXED_NOW.plusMinutes(30), FIXED_NOW);
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));

		// When & Then — habit.deadlineTime은 이미 지났지만 cycle.deadline이 우선 적용되어 통과
		assertThatCode(() -> service.validateHabitUploadAccess(HABIT_ID, USER_ID)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("TEXT 습관 — UPLOAD_SESSION_NOT_REQUIRED(V017)")
	void textHabit_throws() {
		// Given
		Habit habit = Habit.of(HABIT_ID, USER_ID, "습관", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ACTIVE, FIXED_NOW.minusDays(1), null, null);
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));

		// When & Then
		assertThatThrownBy(() -> service.validateHabitUploadAccess(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_REQUIRED);
	}

	@Test
	@DisplayName("PAUSED 습관 — HABIT_NOT_ACTIVE(HB008)")
	void pausedHabit_throws() {
		// Given
		Habit habit = photoHabit(HabitStatus.PAUSED, LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));

		// When & Then
		assertThatThrownBy(() -> service.validateHabitUploadAccess(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("타인 습관 — HABIT_ACCESS_DENIED(HB005)")
	void notOwner_throws() {
		// Given
		Habit habit = photoHabit(HabitStatus.ACTIVE, LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));

		// When & Then
		assertThatThrownBy(() -> service.validateHabitUploadAccess(HABIT_ID, "other-user"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_ACCESS_DENIED);
	}

	@Test
	@DisplayName("ENDED 습관 — HABIT_NOT_FOUND(HB001)")
	void endedHabit_throws() {
		// Given
		Habit habit = Habit.of(HABIT_ID, USER_ID, "습관", HabitVerificationType.PHOTO,
				LocalTime.of(23, 59, 59), HabitStatus.ENDED, FIXED_NOW.minusDays(10), FIXED_NOW.minusDays(1), null);
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));

		// When & Then
		assertThatThrownBy(() -> service.validateHabitUploadAccess(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_FOUND);
	}

	@Test
	@DisplayName("습관 없음 — HABIT_NOT_FOUND(HB001)")
	void habitNotFound_throws() {
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.validateHabitUploadAccess(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_FOUND);
	}

	@Test
	@DisplayName("오늘 마감+grace 경과(활성 사이클 없음) — VERIFICATION_DEADLINE_EXCEEDED(V002)")
	void todayDeadlinePassed_throws() {
		// Given — 마감이 6분 전(grace 5분 초과)
		Habit habit = photoHabit(HabitStatus.ACTIVE, LocalTime.of(13, 54));
		given(habitRepositoryPort.findById(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> service.validateHabitUploadAccess(HABIT_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	private Habit photoHabit(HabitStatus status, LocalTime deadlineTime) {
		return Habit.of(HABIT_ID, USER_ID, "달리기 30분", HabitVerificationType.PHOTO,
				deadlineTime, status, FIXED_NOW.minusDays(1), null, null);
	}
}
