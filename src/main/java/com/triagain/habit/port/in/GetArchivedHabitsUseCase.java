package com.triagain.habit.port.in;

import java.util.List;

import com.triagain.habit.api.ArchivedHabitResponse;

public interface GetArchivedHabitsUseCase {

	/** 지난기록(종료한 습관) 조회 — 마이페이지 솔로 섹션 데이터 소스, status=ENDED만 ended_at 내림차순 */
	List<ArchivedHabitResponse> getArchivedHabits(String userId);
}
