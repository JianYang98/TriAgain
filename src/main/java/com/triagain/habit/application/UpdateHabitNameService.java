package com.triagain.habit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.habit.api.HabitResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.port.in.UpdateHabitNameUseCase;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 습관 이름 수정 — 상태 전이가 없는 단순 수정이라 D13 락 미참여(step4 §3 락 대상 목록 밖) */
@Service
@RequiredArgsConstructor
public class UpdateHabitNameService implements UpdateHabitNameUseCase {

	private final HabitRepositoryPort habitRepositoryPort;

	@Override
	@Transactional
	public HabitResponse updateHabitName(UpdateHabitNameCommand command) {
		Habit habit = HabitAccessGuard.requireOwned(
				habitRepositoryPort.findById(command.habitId()), command.userId());
		habit.rename(command.name());
		Habit saved = habitRepositoryPort.save(habit);
		return HabitResponse.from(saved);
	}
}
