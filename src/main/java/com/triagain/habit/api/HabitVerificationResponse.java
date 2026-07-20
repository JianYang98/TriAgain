package com.triagain.habit.api;

import java.time.LocalDate;

import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.model.HabitVerification;
import com.triagain.habit.domain.vo.HabitCycleStatus;

/** 솔로 인증 생성 응답 — cycle.status가 SUCCESS면 FE가 성공 연출을 노출한다 (step2 §8) */
public record HabitVerificationResponse(
		String verificationId,
		String habitCycleId,
		String habitId,
		String imageUrl,
		String textContent,
		LocalDate targetDate,
		int attemptNumber,
		CycleProgress cycle
) {

	/** 인증 도메인 모델 + (recordCompletion 반영된) 사이클 → 응답 변환 */
	public static HabitVerificationResponse from(HabitVerification verification, HabitCycle cycle) {
		return new HabitVerificationResponse(
				verification.getId(),
				verification.getHabitCycleId(),
				verification.getHabitId(),
				verification.getImageUrl(),
				verification.getTextContent(),
				verification.getTargetDate(),
				verification.getAttemptNumber(),
				new CycleProgress(cycle.getCompletedDays(), cycle.getTargetDays(), cycle.getStatus())
		);
	}

	public record CycleProgress(int completedDays, int targetDays, HabitCycleStatus status) {
	}
}
