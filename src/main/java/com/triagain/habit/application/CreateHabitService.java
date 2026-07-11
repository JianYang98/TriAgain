package com.triagain.habit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.port.in.CreateHabitUseCase;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateHabitService implements CreateHabitUseCase {

	private final HabitRepositoryPort habitRepositoryPort;

	@Override
	@Transactional
	public HabitResponse createHabit(CreateHabitCommand command) {
		Habit habit = Habit.create(
				command.userId(), command.name(), command.verificationType(), command.deadlineTime(),
				command.verificationContent());
		Habit saved = habitRepositoryPort.save(habit);
		return HabitResponse.from(saved);
	}
}
