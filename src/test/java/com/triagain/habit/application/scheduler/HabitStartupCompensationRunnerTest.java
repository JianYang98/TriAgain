package com.triagain.habit.application.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HabitStartupCompensationRunnerTest {

	@Mock
	private FailExpiredHabitCyclesScheduler failExpiredHabitCyclesScheduler;

	private HabitStartupCompensationRunner runner;

	@BeforeEach
	void setUp() {
		runner = new HabitStartupCompensationRunner(failExpiredHabitCyclesScheduler);
	}

	@Test
	@DisplayName("부팅 시 습관 사이클 실패 보정을 호출한다")
	void compensate_callsFailExpiredHabitCycles() {
		// When
		runner.compensateMissedHabitSchedulerJobs();

		// Then
		verify(failExpiredHabitCyclesScheduler).failExpiredHabitCycles();
	}

	@Test
	@DisplayName("보정 단계에서 예외가 발생해도 부팅 자체는 실패하지 않는다(예외 격리)")
	void stepThrows_doesNotPropagate() {
		// Given
		doThrow(new RuntimeException("boom")).when(failExpiredHabitCyclesScheduler).failExpiredHabitCycles();

		// When & Then
		assertThatCode(() -> runner.compensateMissedHabitSchedulerJobs()).doesNotThrowAnyException();
	}
}
