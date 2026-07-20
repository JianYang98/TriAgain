package com.triagain.habit.port.in;

import java.util.List;

import com.triagain.habit.api.HabitListItemResponse;

public interface GetMyHabitsUseCase {

	/** 내 습관 목록 조회 — 홈 탭의 솔로 데이터 소스, status IN(ACTIVE,PAUSED)만 (ENDED는 지난기록에서 별도 조회) */
	List<HabitListItemResponse> getMyHabits(String userId);
}
