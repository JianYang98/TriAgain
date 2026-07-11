package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.habit.api.HabitListItemResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

@ExtendWith(MockitoExtension.class)
class GetMyHabitsServiceTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 5, 14, 0, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW.atZone(ZONE).toInstant(), ZONE);
	private static final LocalDate TODAY = FIXED_NOW.toLocalDate();

	private static final String USER_ID = "user-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	@Mock
	private HabitVerificationRepositoryPort habitVerificationRepositoryPort;

	private GetMyHabitsService service;

	@BeforeEach
	void setUp() {
		service = new GetMyHabitsService(
				habitRepositoryPort, habitCycleRepositoryPort, habitVerificationRepositoryPort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("ACTIVE/PAUSED 습관만 조회하고 ENDED는 제외한다(status<>ENDED 필터)")
	void excludesEndedHabits() {
		// Given
		Habit active = habit("habit-1", HabitStatus.ACTIVE);
		given(habitRepositoryPort.findAllByUserIdAndStatusNot(USER_ID, HabitStatus.ENDED))
				.willReturn(List.of(active));
		given(habitCycleRepositoryPort.countSuccessByHabitIds(List.of("habit-1")))
				.willReturn(Map.of("habit-1", 4));
		given(habitCycleRepositoryPort.findAllByHabitIdInAndStatus(List.of("habit-1"), HabitCycleStatus.IN_PROGRESS))
				.willReturn(List.of());
		given(habitVerificationRepositoryPort.findVerifiedHabitIds(List.of("habit-1"), TODAY))
				.willReturn(Set.of());

		// When
		List<HabitListItemResponse> result = service.getMyHabits(USER_ID);

		// Then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).habitId()).isEqualTo("habit-1");
		assertThat(result.get(0).successCount()).isEqualTo(4);
		assertThat(result.get(0).todayVerified()).isFalse();
		assertThat(result.get(0).activeCycle()).isNull();
	}

	@Test
	@DisplayName("IN_PROGRESS 사이클이 있으면 activeCycle에 매핑되고, 오늘 인증했으면 todayVerified=true")
	void mapsActiveCycleAndTodayVerified() {
		// Given
		Habit active = habit("habit-1", HabitStatus.ACTIVE);
		HabitCycle cycle = HabitCycle.of("HCYC-1", "habit-1", USER_ID, 3, 3, 1,
				HabitCycleStatus.IN_PROGRESS, TODAY, FIXED_NOW.plusDays(2), FIXED_NOW);
		given(habitRepositoryPort.findAllByUserIdAndStatusNot(USER_ID, HabitStatus.ENDED))
				.willReturn(List.of(active));
		given(habitCycleRepositoryPort.countSuccessByHabitIds(List.of("habit-1")))
				.willReturn(Map.of());
		given(habitCycleRepositoryPort.findAllByHabitIdInAndStatus(List.of("habit-1"), HabitCycleStatus.IN_PROGRESS))
				.willReturn(List.of(cycle));
		given(habitVerificationRepositoryPort.findVerifiedHabitIds(List.of("habit-1"), TODAY))
				.willReturn(Set.of("habit-1"));

		// When
		List<HabitListItemResponse> result = service.getMyHabits(USER_ID);

		// Then
		assertThat(result.get(0).activeCycle()).isNotNull();
		assertThat(result.get(0).activeCycle().cycleId()).isEqualTo("HCYC-1");
		assertThat(result.get(0).todayVerified()).isTrue();
		assertThat(result.get(0).successCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("습관이 없으면 배치 조회를 스킵하고 빈 리스트를 반환한다")
	void noHabits_returnsEmptyWithoutBatchQueries() {
		// Given
		given(habitRepositoryPort.findAllByUserIdAndStatusNot(USER_ID, HabitStatus.ENDED))
				.willReturn(List.of());

		// When
		List<HabitListItemResponse> result = service.getMyHabits(USER_ID);

		// Then
		assertThat(result).isEmpty();
	}

	private Habit habit(String id, HabitStatus status) {
		return Habit.of(id, USER_ID, "매일 물 2L", HabitVerificationType.TEXT,
				LocalTime.of(23, 59, 59), status, FIXED_NOW.minusDays(10), null, null);
	}
}
