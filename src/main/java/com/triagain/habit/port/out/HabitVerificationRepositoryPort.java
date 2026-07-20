package com.triagain.habit.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.triagain.habit.domain.model.HabitVerification;

public interface HabitVerificationRepositoryPort {

	/** 인증 저장 — 생성 시 사용 */
	HabitVerification save(HabitVerification verification);

	/** 습관·날짜로 인증 존재 여부 확인 — 오늘 중복 인증(V003)·좀비 사이클 가드(D12)에 사용 */
	boolean existsByHabitIdAndTargetDate(String habitId, LocalDate targetDate);

	/** 습관 묶음 중 특정 날짜에 인증된 습관 ID 배치 조회 — 홈 목록 todayVerified N+1 방지 */
	Set<String> findVerifiedHabitIds(List<String> habitIds, LocalDate targetDate);
}
