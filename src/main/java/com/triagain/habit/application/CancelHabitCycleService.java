package com.triagain.habit.application;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.port.in.CancelHabitCycleUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 시작 전 사이클 취소 — today &lt; startDate인 IN_PROGRESS만 hard delete(HB007, step1 §5 엣지 7) */
@Service
@RequiredArgsConstructor
public class CancelHabitCycleService implements CancelHabitCycleUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;
	private final Clock clock;

	@Override
	@Transactional
	public void cancelCurrentCycle(String habitId, String userId) {
		HabitAccessGuard.requireOwned(habitRepositoryPort.findByIdForUpdate(habitId), userId);

		HabitCycle cycle = habitCycleRepositoryPort
				.findByHabitIdAndStatus(habitId, HabitCycleStatus.IN_PROGRESS)
				.orElseThrow(() -> new BusinessException(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS));

		if (!LocalDate.now(clock).isBefore(cycle.getStartDate())) {
			throw new BusinessException(ErrorCode.HABIT_CYCLE_CANCEL_NOT_ALLOWED);
		}

		habitCycleRepositoryPort.deleteById(cycle.getId());
	}
}
