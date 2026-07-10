package com.triagain.habit.api;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;

/** 습관 단건 응답 — 등록·이름수정·종료·멈춤·재개 공통 (step2 §1·3·4·5). endedAt은 ENDED 전이 전엔 null */
public record HabitResponse(
		String habitId,
		String name,
		HabitVerificationType verificationType,
		LocalTime deadlineTime,
		HabitStatus status,
		LocalDateTime createdAt,
		LocalDateTime endedAt
) {

	/** 습관 도메인 모델 → 응답 변환 — 등록·이름수정·종료·멈춤·재개 서비스 공용 */
	public static HabitResponse from(Habit habit) {
		return new HabitResponse(
				habit.getId(),
				habit.getName(),
				habit.getVerificationType(),
				habit.getDeadlineTime(),
				habit.getStatus(),
				habit.getCreatedAt(),
				habit.getEndedAt()
		);
	}
}
