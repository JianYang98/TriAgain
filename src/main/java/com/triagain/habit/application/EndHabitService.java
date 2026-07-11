package com.triagain.habit.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.port.in.EndHabitUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 습관 종료(아카이브) — IN_PROGRESS 사이클이 있으면 같은 트랜잭션에서 fail() 처리(step1 §2-2, "좀비 IN_PROGRESS 방지") */
@Service
@RequiredArgsConstructor
public class EndHabitService implements EndHabitUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;

	@Override
	@Transactional
	public HabitResponse endHabit(String habitId, String userId) {
		Habit habit = HabitAccessGuard.requireOwned(habitRepositoryPort.findByIdForUpdate(habitId), userId);

		Optional<HabitCycle> inProgress = habitCycleRepositoryPort
				.findByHabitIdAndStatus(habitId, HabitCycleStatus.IN_PROGRESS);
		inProgress.ifPresent(cycle -> {
			cycle.fail();
			habitCycleRepositoryPort.save(cycle);
		});

		habit.end();
		Habit saved = habitRepositoryPort.save(habit);
		return HabitResponse.from(saved);
	}
}
