package com.triagain.habit.api;

import java.time.LocalDateTime;

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitVerificationType;

/** 종료한 습관(지난기록) 응답 — 마이페이지 솔로 섹션, 읽기전용(재개/재시작 액션 없음) (step2 §4-2) */
public record ArchivedHabitResponse(
		String habitId,
		String name,
		HabitVerificationType verificationType,
		int successCount,
		LocalDateTime endedAt
) {

	/** 습관 도메인 모델 + 성공 횟수 → 지난기록 응답 변환 */
	public static ArchivedHabitResponse from(Habit habit, int successCount) {
		return new ArchivedHabitResponse(
				habit.getId(),
				habit.getName(),
				habit.getVerificationType(),
				successCount,
				habit.getEndedAt()
		);
	}
}
