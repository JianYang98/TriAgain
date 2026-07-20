package com.triagain.habit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.domain.vo.HabitCycleStatus;

class HabitCycleTest {

	private static final LocalDate TODAY = LocalDate.now();
	private static final LocalDateTime FUTURE_DEADLINE = LocalDateTime.now().plusDays(3);

	@Nested
	@DisplayName("start — 사이클 시작(첫 시작/재시작 통합)")
	class Start {

		@Test
		@DisplayName("targetDays 3, completedDays 0, IN_PROGRESS로 생성된다")
		void success() {
			// Given & When
			HabitCycle cycle = HabitCycle.start("habit-1", "user-1", 1, TODAY, FUTURE_DEADLINE);

			// Then
			assertThat(cycle.getId()).startsWith("HCYC");
			assertThat(cycle.getHabitId()).isEqualTo("habit-1");
			assertThat(cycle.getUserId()).isEqualTo("user-1");
			assertThat(cycle.getCycleNumber()).isEqualTo(1);
			assertThat(cycle.getTargetDays()).isEqualTo(3);
			assertThat(cycle.getCompletedDays()).isEqualTo(0);
			assertThat(cycle.getStatus()).isEqualTo(HabitCycleStatus.IN_PROGRESS);
			assertThat(cycle.getStartDate()).isEqualTo(TODAY);
			assertThat(cycle.getDeadline()).isEqualTo(FUTURE_DEADLINE);
		}

		@Test
		@DisplayName("재시작 사이클도 첫 시작과 동일 로직 — cycleNumber만 다르다(D3, 첫/재시작 이원화 없음)")
		void restart_sameLogicDifferentCycleNumber() {
			// Given & When
			HabitCycle cycle = HabitCycle.start("habit-1", "user-1", 4, TODAY, FUTURE_DEADLINE);

			// Then
			assertThat(cycle.getCycleNumber()).isEqualTo(4);
			assertThat(cycle.getCompletedDays()).isEqualTo(0);
			assertThat(cycle.getStatus()).isEqualTo(HabitCycleStatus.IN_PROGRESS);
		}
	}

	@Nested
	@DisplayName("recordCompletion — 인증 완료 기록")
	class RecordCompletion {

		@Test
		@DisplayName("completedDays가 1 증가하고 IN_PROGRESS를 유지한다")
		void incrementStaysInProgress() {
			// Given
			HabitCycle cycle = inProgressCycle(0);

			// When
			cycle.recordCompletion();

			// Then
			assertThat(cycle.getCompletedDays()).isEqualTo(1);
			assertThat(cycle.getStatus()).isEqualTo(HabitCycleStatus.IN_PROGRESS);
		}

		@Test
		@DisplayName("completedDays가 targetDays(3)에 도달하면 SUCCESS로 전환된다")
		void thirdCompletion_triggersSuccess() {
			// Given — 2/3 완료 상태
			HabitCycle cycle = inProgressCycle(2);

			// When
			cycle.recordCompletion();

			// Then
			assertThat(cycle.getCompletedDays()).isEqualTo(3);
			assertThat(cycle.getStatus()).isEqualTo(HabitCycleStatus.SUCCESS);
		}

		@Test
		@DisplayName("SUCCESS 상태에서 호출하면 HABIT_CYCLE_NOT_IN_PROGRESS 예외가 발생한다")
		void alreadySuccess_throws() {
			HabitCycle cycle = cycleWithStatus(HabitCycleStatus.SUCCESS, 3);

			assertThatThrownBy(cycle::recordCompletion)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
		}

		@Test
		@DisplayName("FAILED 상태에서 호출하면 HABIT_CYCLE_NOT_IN_PROGRESS 예외가 발생한다")
		void alreadyFailed_throws() {
			HabitCycle cycle = cycleWithStatus(HabitCycleStatus.FAILED, 1);

			assertThatThrownBy(cycle::recordCompletion)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
		}
	}

	@Nested
	@DisplayName("fail — 사이클 실패 처리")
	class Fail {

		@Test
		@DisplayName("IN_PROGRESS → FAILED 상태 전환에 성공한다")
		void success() {
			// Given
			HabitCycle cycle = inProgressCycle(1);

			// When
			cycle.fail();

			// Then
			assertThat(cycle.getStatus()).isEqualTo(HabitCycleStatus.FAILED);
		}

		@Test
		@DisplayName("SUCCESS 상태에서 fail하면 예외가 발생한다")
		void alreadySuccess_throws() {
			HabitCycle cycle = cycleWithStatus(HabitCycleStatus.SUCCESS, 3);

			assertThatThrownBy(cycle::fail)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
		}

		@Test
		@DisplayName("이미 FAILED 상태에서 중복 fail하면 예외가 발생한다")
		void duplicateFail_throws() {
			HabitCycle cycle = cycleWithStatus(HabitCycleStatus.FAILED, 1);

			assertThatThrownBy(cycle::fail)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
		}
	}

	// --- 헬퍼 메서드 ---

	private HabitCycle inProgressCycle(int completedDays) {
		return HabitCycle.of("HCYC-1", "habit-1", "user-1", 1, 3, completedDays,
				HabitCycleStatus.IN_PROGRESS, TODAY, FUTURE_DEADLINE, LocalDateTime.now());
	}

	private HabitCycle cycleWithStatus(HabitCycleStatus status, int completedDays) {
		return HabitCycle.of("HCYC-1", "habit-1", "user-1", 1, 3, completedDays,
				status, TODAY, FUTURE_DEADLINE, LocalDateTime.now());
	}
}
