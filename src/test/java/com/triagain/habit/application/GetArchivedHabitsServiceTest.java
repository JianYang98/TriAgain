package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.habit.api.ArchivedHabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

@ExtendWith(MockitoExtension.class)
class GetArchivedHabitsServiceTest {

	private static final String USER_ID = "user-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	private GetArchivedHabitsService service;

	@BeforeEach
	void setUp() {
		service = new GetArchivedHabitsService(habitRepositoryPort, habitCycleRepositoryPort);
	}

	@Test
	@DisplayName("ENDED 습관을 ended_at 내림차순으로 조회하고, 종료 시점 successCount를 포함한다")
	void returnsEndedHabitsWithSuccessCount() {
		// Given
		LocalDateTime endedAt = LocalDateTime.of(2026, 7, 5, 21, 30, 0);
		Habit ended = Habit.of("habit-1", USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), HabitStatus.ENDED, endedAt.minusDays(10), endedAt, null);
		given(habitRepositoryPort.findAllByUserIdAndStatusOrderByEndedAtDesc(USER_ID, HabitStatus.ENDED))
				.willReturn(List.of(ended));
		given(habitCycleRepositoryPort.countSuccessByHabitIds(List.of("habit-1")))
				.willReturn(Map.of("habit-1", 6));

		// When
		List<ArchivedHabitResponse> result = service.getArchivedHabits(USER_ID);

		// Then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).habitId()).isEqualTo("habit-1");
		assertThat(result.get(0).successCount()).isEqualTo(6);
		assertThat(result.get(0).endedAt()).isEqualTo(endedAt);
	}

	@Test
	@DisplayName("종료한 습관이 없으면 배치 조회를 스킵하고 빈 리스트를 반환한다")
	void noArchivedHabits_returnsEmpty() {
		// Given
		given(habitRepositoryPort.findAllByUserIdAndStatusOrderByEndedAtDesc(USER_ID, HabitStatus.ENDED))
				.willReturn(List.of());

		// When
		List<ArchivedHabitResponse> result = service.getArchivedHabits(USER_ID);

		// Then
		assertThat(result).isEmpty();
	}
}
