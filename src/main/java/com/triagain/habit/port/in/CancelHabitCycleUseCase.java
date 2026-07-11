package com.triagain.habit.port.in;

public interface CancelHabitCycleUseCase {

	/** 시작 전 사이클 취소 — today < startDate인 IN_PROGRESS 사이클만 hard delete 가능(HB007) */
	void cancelCurrentCycle(String habitId, String userId);
}
