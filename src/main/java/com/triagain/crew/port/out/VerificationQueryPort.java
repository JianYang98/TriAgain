package com.triagain.crew.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Crew → Verification 컨텍스트 간 조회 포트 — 오늘 인증 현황 조회에 사용 */
public interface VerificationQueryPort {

    Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate);

    /** 유저·크루 묶음별 APPROVED 인증 일수 조회 — 홈 완료 탭 verifiedDayCount 배치 집계에 사용 */
    Map<String, Integer> findApprovedDayCountsByCrewIds(String userId, List<String> crewIds);
}
