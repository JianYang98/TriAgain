package com.triagain.habit.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;

/** 작심 사이클 응답 — 사이클 시작 응답(§6) + 습관 목록의 activeCycle 중첩 필드(§2) 공용 */
public record HabitCycleResponse(
		String cycleId,
		int cycleNumber,
		int completedDays,
		int targetDays,
		HabitCycleStatus status,
		LocalDate startDate,
		LocalDateTime deadline
) {

	/** 사이클 도메인 모델 → 응답 변환 */
	public static HabitCycleResponse from(HabitCycle cycle) {
		return new HabitCycleResponse(
				cycle.getId(),
				cycle.getCycleNumber(),
				cycle.getCompletedDays(),
				cycle.getTargetDays(),
				cycle.getStatus(),
				cycle.getStartDate(),
				cycle.getDeadline()
		);
	}
}
