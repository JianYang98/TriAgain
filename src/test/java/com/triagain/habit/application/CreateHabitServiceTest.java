package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.in.CreateHabitUseCase.CreateHabitCommand;
import com.triagain.habit.port.out.HabitRepositoryPort;

@ExtendWith(MockitoExtension.class)
class CreateHabitServiceTest {

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	private CreateHabitService service;

	@BeforeEach
	void setUp() {
		service = new CreateHabitService(habitRepositoryPort);
	}

	@Test
	@DisplayName("습관 등록 — status=ACTIVE로 생성되고 사이클은 만들지 않는다(D3)")
	void createHabit_success() {
		// Given
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.createHabit(new CreateHabitCommand(
				"user-1", "매일 물 2L", HabitVerificationType.TEXT, LocalTime.of(23, 59, 59), null));

		// Then
		assertThat(result.habitId()).startsWith("HBIT");
		assertThat(result.status()).isEqualTo(HabitStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("매일 물 2L");
		assertThat(result.verificationContent()).isNull();
	}

	@Test
	@DisplayName("verificationContent를 지정하면 저장되어 응답에 그대로 반환된다 (지시서 05 #3)")
	void createHabit_withVerificationContent() {
		// Given
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.createHabit(new CreateHabitCommand(
				"user-1", "매일 물 2L", HabitVerificationType.TEXT, LocalTime.of(23, 59, 59), "운동 완료 인증샷 찍기"));

		// Then
		assertThat(result.verificationContent()).isEqualTo("운동 완료 인증샷 찍기");
	}

	@Test
	@DisplayName("deadlineTime 미지정 시 기본값 23:59:59가 적용된다")
	void defaultDeadlineTime() {
		// Given
		given(habitRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitResponse result = service.createHabit(new CreateHabitCommand(
				"user-1", "매일 물 2L", HabitVerificationType.TEXT, null, null));

		// Then
		assertThat(result.deadlineTime()).isEqualTo(LocalTime.of(23, 59, 59));
	}
}
