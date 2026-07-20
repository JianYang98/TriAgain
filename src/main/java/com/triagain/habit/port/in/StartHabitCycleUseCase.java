package com.triagain.habit.port.in;

import com.triagain.habit.api.HabitCycleResponse;
import com.triagain.habit.domain.vo.CycleStartOption;

public interface StartHabitCycleUseCase {

	/** 작심 사이클 시작(첫 시작/재시작 통합) — TODAY는 마감 전+오늘 미인증 가드, TOMORROW는 무조건 허용 */
	StartCycleResult startCycle(StartHabitCycleCommand command);

	record StartHabitCycleCommand(String userId, String habitId, CycleStartOption startOption) {
	}

	/** created=false면 더블탭 멱등 반환(기존 IN_PROGRESS 재조회) — 컨트롤러가 200/201 결정에 사용(step2 §6) */
	record StartCycleResult(HabitCycleResponse cycle, boolean created) {
	}
}
