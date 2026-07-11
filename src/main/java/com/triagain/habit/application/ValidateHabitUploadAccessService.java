package com.triagain.habit.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.domain.DeadlinePolicy;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.in.ValidateHabitUploadAccessUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 솔로 업로드 세션 발급 가능 여부 검증 — verification BC의 HabitPort가 크로스 컨텍스트로 호출(step2 §9, crew validateCrewAndDeadline 대칭) */
@Service
@RequiredArgsConstructor
public class ValidateHabitUploadAccessService implements ValidateHabitUploadAccessUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public void validateHabitUploadAccess(String habitId, String userId) {
		Habit habit = HabitAccessGuard.requireOwned(habitRepositoryPort.findById(habitId), userId);
		if (habit.getVerificationType() == HabitVerificationType.TEXT) {
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_REQUIRED);
		}
		if (habit.getStatus() != HabitStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.HABIT_NOT_ACTIVE);
		}

		LocalDateTime deadline = habitCycleRepositoryPort
				.findByHabitIdAndStatus(habitId, HabitCycleStatus.IN_PROGRESS)
				.map(HabitCycle::getDeadline)
				.orElseGet(() -> DeadlinePolicy.todayDeadline(habit.getDeadlineTime(), clock));

		if (!DeadlinePolicy.isWithinDeadline(LocalDateTime.now(clock), deadline)) {
			throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
		}
	}
}
