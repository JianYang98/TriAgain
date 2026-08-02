package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
import org.springframework.dao.DataIntegrityViolationException;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.CycleStartOption;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.in.StartHabitCycleUseCase.StartCycleResult;
import com.triagain.habit.port.in.StartHabitCycleUseCase.StartHabitCycleCommand;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

@ExtendWith(MockitoExtension.class)
class StartHabitCycleServiceTest {

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

	@Mock
	private HabitVerificationRepositoryPort habitVerificationRepositoryPort;

	private StartHabitCycleService service;

	@BeforeEach
	void setUp() {
		service = new StartHabitCycleService(
				habitRepositoryPort, habitCycleRepositoryPort, habitVerificationRepositoryPort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("첫 시작 — cycleNumber=1로 생성된다")
	void firstStart_cycleNumberOne() {
		// Given
		Habit habit = activeHabit(LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitCycleRepositoryPort.findMaxCycleNumber(HABIT_ID)).willReturn(0);
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		StartCycleResult result = service.startCycle(new StartHabitCycleCommand(USER_ID, HABIT_ID,
				CycleStartOption.TODAY));

		// Then
		assertThat(result.created()).isTrue();
		assertThat(result.cycle().cycleNumber()).isEqualTo(1);
		assertThat(result.cycle().startDate()).isEqualTo(TODAY);
	}

	@Test
	@DisplayName("FAILED 후 재시작 — maxCycleNumber+1로 생성된다")
	void restartAfterFailed_nextCycleNumber() {
		// Given
		Habit habit = activeHabit(LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitCycleRepositoryPort.findMaxCycleNumber(HABIT_ID)).willReturn(2);
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		StartCycleResult result = service.startCycle(new StartHabitCycleCommand(USER_ID, HABIT_ID,
				CycleStartOption.TODAY));

		// Then
		assertThat(result.cycle().cycleNumber()).isEqualTo(3);
	}

	@Test
	@DisplayName("TODAY 시작 — 마감+grace 경과 시 VERIFICATION_DEADLINE_EXCEEDED")
	void todayStart_deadlinePassed_throws() {
		// Given — 마감이 6분 전(grace 5분 초과)
		Habit habit = activeHabit(LocalTime.of(13, 54));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> service.startCycle(
				new StartHabitCycleCommand(USER_ID, HABIT_ID, CycleStartOption.TODAY)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("TODAY 시작 — 오늘 이미 인증됨(좀비 사이클 방지) → VERIFICATION_ALREADY_EXISTS")
	void todayStart_alreadyVerifiedToday_throws() {
		// Given
		Habit habit = activeHabit(LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(true);

		// When & Then
		assertThatThrownBy(() -> service.startCycle(
				new StartHabitCycleCommand(USER_ID, HABIT_ID, CycleStartOption.TODAY)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ALREADY_EXISTS);
	}

	@Test
	@DisplayName("TOMORROW 시작 — 마감 경과·오늘 인증됨과 무관하게 통과한다")
	void tomorrowStart_ignoresDeadlineAndTodayVerifiedGuards() {
		// Given — 마감 이미 지남
		Habit habit = activeHabit(LocalTime.of(0, 0, 0));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());
		given(habitCycleRepositoryPort.findMaxCycleNumber(HABIT_ID)).willReturn(0);
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		StartCycleResult result = service.startCycle(
				new StartHabitCycleCommand(USER_ID, HABIT_ID, CycleStartOption.TOMORROW));

		// Then
		assertThat(result.cycle().startDate()).isEqualTo(TODAY.plusDays(1));
	}

	@Test
	@DisplayName("PAUSED 습관 — HABIT_NOT_ACTIVE 예외가 발생한다")
	void pausedHabit_throws() {
		// Given
		Habit habit = Habit.of(HABIT_ID, USER_ID, "습관", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.PAUSED, FIXED_NOW.minusDays(1), null, null);
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));

		// When & Then
		assertThatThrownBy(() -> service.startCycle(
				new StartHabitCycleCommand(USER_ID, HABIT_ID, CycleStartOption.TODAY)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("이미 IN_PROGRESS 사이클 존재 — HABIT_CYCLE_ALREADY_IN_PROGRESS 예외가 발생한다")
	void alreadyInProgress_throws() {
		// Given
		Habit habit = activeHabit(LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		HabitCycle existing = HabitCycle.of("HCYC-1", HABIT_ID, USER_ID, 1, 3, 0,
				HabitCycleStatus.IN_PROGRESS, TODAY, FIXED_NOW.plusDays(3), FIXED_NOW);
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(existing));

		// When & Then
		assertThatThrownBy(() -> service.startCycle(
				new StartHabitCycleCommand(USER_ID, HABIT_ID, CycleStartOption.TODAY)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_CYCLE_ALREADY_IN_PROGRESS);
	}

	@Test
	@DisplayName("더블탭(유니크 제약 위반) — 기존 IN_PROGRESS를 재조회해 created=false로 멱등 반환한다")
	void doubleTap_returnsExistingIdempotently() {
		// Given
		Habit habit = activeHabit(LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
		HabitCycle existingFromOtherThread = HabitCycle.of("HCYC-OTHER", HABIT_ID, USER_ID, 1, 3, 0,
				HabitCycleStatus.IN_PROGRESS, TODAY, FIXED_NOW.plusDays(3), FIXED_NOW);
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty())
				.willReturn(Optional.of(existingFromOtherThread));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitCycleRepositoryPort.findMaxCycleNumber(HABIT_ID)).willReturn(0);
		given(habitCycleRepositoryPort.save(any())).willThrow(new DataIntegrityViolationException("UK violation"));

		// When
		StartCycleResult result = service.startCycle(
				new StartHabitCycleCommand(USER_ID, HABIT_ID, CycleStartOption.TODAY));

		// Then
		assertThat(result.created()).isFalse();
		assertThat(result.cycle().cycleId()).isEqualTo("HCYC-OTHER");
	}

	@Test
	@DisplayName("습관 없음 — HABIT_NOT_FOUND 예외가 발생한다")
	void habitNotFound_throws() {
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.startCycle(
				new StartHabitCycleCommand(USER_ID, HABIT_ID, CycleStartOption.TODAY)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_FOUND);
	}

	@Test
	@DisplayName("타인 습관 — HABIT_ACCESS_DENIED 예외가 발생한다")
	void notOwner_throws() {
		Habit habit = activeHabit(LocalTime.of(23, 59, 59));
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));

		assertThatThrownBy(() -> service.startCycle(
				new StartHabitCycleCommand("other-user", HABIT_ID, CycleStartOption.TODAY)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_ACCESS_DENIED);
	}

	private Habit activeHabit(LocalTime deadlineTime) {
		return Habit.of(HABIT_ID, USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				deadlineTime, HabitStatus.ACTIVE, FIXED_NOW.minusDays(1), null, null);
	}
}
