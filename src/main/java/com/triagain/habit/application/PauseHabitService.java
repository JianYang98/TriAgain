package com.triagain.habit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.port.in.PauseHabitUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PauseHabitService implements PauseHabitUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;

	@Override
	@Transactional
	public HabitResponse pauseHabit(String habitId, String userId) {
		Habit habit = HabitAccessGuard.requireOwned(habitRepositoryPort.findByIdForUpdate(habitId), userId);

		if (habitCycleRepositoryPort.findByHabitIdAndStatus(habitId, HabitCycleStatus.IN_PROGRESS).isPresent()) {
			throw new BusinessException(ErrorCode.HABIT_PAUSE_NOT_ALLOWED);
		}

		habit.pause();
		Habit saved = habitRepositoryPort.save(habit);
		return HabitResponse.from(saved);
	}
}
