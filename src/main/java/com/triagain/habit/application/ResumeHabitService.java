package com.triagain.habit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.port.in.ResumeHabitUseCase;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResumeHabitService implements ResumeHabitUseCase {

	private final HabitRepositoryPort habitRepositoryPort;

	@Override
	@Transactional
	public HabitResponse resumeHabit(String habitId, String userId) {
		Habit habit = HabitAccessGuard.requireOwned(habitRepositoryPort.findByIdForUpdate(habitId), userId);

		habit.resume();
		Habit saved = habitRepositoryPort.save(habit);
		return HabitResponse.from(saved);
	}
}
