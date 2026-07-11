package com.triagain.habit.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.habit.api.ArchivedHabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.port.in.GetArchivedHabitsUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 지난기록 조회 — status=ENDED인 본인 습관, ended_at 내림차순 + successCount 배치 조회(step2 §4-2) */
@Service
@RequiredArgsConstructor
public class GetArchivedHabitsService implements GetArchivedHabitsUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;

	@Override
	@Transactional(readOnly = true)
	public List<ArchivedHabitResponse> getArchivedHabits(String userId) {
		List<Habit> habits = habitRepositoryPort
				.findAllByUserIdAndStatusOrderByEndedAtDesc(userId, HabitStatus.ENDED);
		if (habits.isEmpty()) {
			return List.of();
		}

		List<String> habitIds = habits.stream().map(Habit::getId).toList();
		Map<String, Integer> successCounts = habitCycleRepositoryPort.countSuccessByHabitIds(habitIds);

		return habits.stream()
				.map(habit -> ArchivedHabitResponse.from(habit, successCounts.getOrDefault(habit.getId(), 0)))
				.toList();
	}
}
