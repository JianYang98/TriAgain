package com.triagain.habit.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.habit.api.HabitListItemResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.port.in.GetMyHabitsUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 홈 목록 조회 — status IN(ACTIVE,PAUSED)인 본인 습관 + successCount/todayVerified/activeCycle 배치 조회(N+1 방지, step2 §2) */
@Service
@RequiredArgsConstructor
public class GetMyHabitsService implements GetMyHabitsUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;
	private final HabitVerificationRepositoryPort habitVerificationRepositoryPort;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public List<HabitListItemResponse> getMyHabits(String userId) {
		List<Habit> habits = habitRepositoryPort.findAllByUserIdAndStatusNot(userId, HabitStatus.ENDED);
		if (habits.isEmpty()) {
			return List.of();
		}

		List<String> habitIds = habits.stream().map(Habit::getId).toList();
		Map<String, Integer> successCounts = habitCycleRepositoryPort.countSuccessByHabitIds(habitIds);
		Map<String, HabitCycle> activeCycles = habitCycleRepositoryPort
				.findAllByHabitIdInAndStatus(habitIds, HabitCycleStatus.IN_PROGRESS).stream()
				.collect(Collectors.toMap(HabitCycle::getHabitId, Function.identity()));
		Set<String> verifiedHabitIds = habitVerificationRepositoryPort
				.findVerifiedHabitIds(habitIds, LocalDate.now(clock));

		return habits.stream()
				.map(habit -> HabitListItemResponse.from(
						habit,
						successCounts.getOrDefault(habit.getId(), 0),
						verifiedHabitIds.contains(habit.getId()),
						activeCycles.get(habit.getId())
				))
				.toList();
	}
}
