package com.triagain.habit.application;

import java.util.Optional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;

/** 습관 소유/상태 가드 — 뮤테이션·검증 서비스가 공유하는 ENDED 제외·소유자·활성 검증(가드 1·1b 중복 제거) */
final class HabitAccessGuard {

	private HabitAccessGuard() {
	}

	/** ENDED 제외(HB001) + 소유자(HB005) 검증 — 락 여부는 호출자가 findById/findByIdForUpdate로 결정 */
	static Habit requireOwned(Optional<Habit> found, String userId) {
		Habit habit = found
				.filter(h -> h.getStatus() != HabitStatus.ENDED)
				.orElseThrow(() -> new BusinessException(ErrorCode.HABIT_NOT_FOUND));
		if (!habit.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.HABIT_ACCESS_DENIED);
		}
		return habit;
	}

	/** requireOwned + 활성 상태(HB008) 검증 — PAUSED/종료 습관의 사이클 시작·인증 차단(가드 1b) */
	static Habit requireOwnedActive(Optional<Habit> found, String userId) {
		Habit habit = requireOwned(found, userId);
		if (habit.getStatus() != HabitStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.HABIT_NOT_ACTIVE);
		}
		return habit;
	}
}
