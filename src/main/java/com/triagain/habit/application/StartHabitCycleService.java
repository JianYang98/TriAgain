package com.triagain.habit.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.domain.DeadlinePolicy;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.api.HabitCycleResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.CycleStartOption;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.port.in.StartHabitCycleUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 작심 사이클 시작(첫 시작/재시작 통합) — TODAY 마감·좀비 사이클 가드 + 더블탭 멱등(step1 §2-3, step4 §3) */
@Service
@RequiredArgsConstructor
public class StartHabitCycleService implements StartHabitCycleUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;
	private final HabitVerificationRepositoryPort habitVerificationRepositoryPort;
	private final Clock clock;

	@Override
	@Transactional
	public StartCycleResult startCycle(StartHabitCycleCommand command) {
		Habit habit = HabitAccessGuard.requireOwnedActive(
				habitRepositoryPort.findByIdForUpdate(command.habitId()), command.userId());

		if (habitCycleRepositoryPort
				.findByHabitIdAndStatus(habit.getId(), HabitCycleStatus.IN_PROGRESS).isPresent()) {
			throw new BusinessException(ErrorCode.HABIT_CYCLE_ALREADY_IN_PROGRESS);
		}

		CycleStartOption startOption = command.startOption() != null
				? command.startOption() : CycleStartOption.TODAY;
		LocalDate today = LocalDate.now(clock);
		LocalDate startDate = today;

		if (startOption == CycleStartOption.TODAY) {
			validateTodayStartGuards(habit, today);
		} else {
			startDate = today.plusDays(1);
		}

		LocalDateTime deadline = startDate.plusDays(HabitCycle.DEFAULT_TARGET_DAYS).atTime(habit.getDeadlineTime());
		int nextCycleNumber = habitCycleRepositoryPort.findMaxCycleNumber(habit.getId()) + 1;
		HabitCycle cycle = HabitCycle.start(habit.getId(), habit.getUserId(), nextCycleNumber, startDate, deadline);

		return saveOrReturnExisting(habit.getId(), cycle);
	}

	/** 저장, 유니크 제약 위반(더블탭) 시 기존 IN_PROGRESS 재조회 후 멱등 반환(created=false, FindOrCreateActiveChallengeService 패턴) */
	private StartCycleResult saveOrReturnExisting(String habitId, HabitCycle cycle) {
		try {
			HabitCycle saved = habitCycleRepositoryPort.save(cycle);
			return new StartCycleResult(HabitCycleResponse.from(saved), true);
		} catch (DataIntegrityViolationException e) {
			HabitCycle existing = habitCycleRepositoryPort
					.findByHabitIdAndStatus(habitId, HabitCycleStatus.IN_PROGRESS)
					.orElseThrow(() -> new BusinessException(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS));
			return new StartCycleResult(HabitCycleResponse.from(existing), false);
		}
	}

	/** TODAY 시작 가드 — 마감+grace 이내(V002) + 오늘 미인증(V003, 좀비 사이클 방지, step1 §2-3) */
	private void validateTodayStartGuards(Habit habit, LocalDate today) {
		LocalDateTime todayDeadline = DeadlinePolicy.todayDeadline(habit.getDeadlineTime(), clock);
		if (!DeadlinePolicy.isWithinDeadline(LocalDateTime.now(clock), todayDeadline)) {
			throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
		}
		if (habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(habit.getId(), today)) {
			throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_EXISTS);
		}
	}
}
