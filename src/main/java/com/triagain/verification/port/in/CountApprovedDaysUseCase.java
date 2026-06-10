package com.triagain.verification.port.in;

import java.util.List;
import java.util.Map;

/** 유저·크루 묶음별 APPROVED 인증 일수 배치 조회 — Crew Context에서 홈 완료 탭 집계에 사용 */
public interface CountApprovedDaysUseCase {

	/** 유저·크루 묶음별 APPROVED 인증 일수 조회 — crewId→일수 Map 반환 */
	Map<String, Integer> countApprovedDaysByCrewIds(String userId, List<String> crewIds);
}
