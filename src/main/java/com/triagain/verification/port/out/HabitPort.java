package com.triagain.verification.port.out;

public interface HabitPort {

	/** 습관 존재·소유자·활성 상태·마감 검증 — 솔로 업로드 세션 발급 가능 여부 판단(HB001/HB005/V017/HB008/V002, step2 §9) */
	void validateHabitAndDeadline(String habitId, String userId);
}
