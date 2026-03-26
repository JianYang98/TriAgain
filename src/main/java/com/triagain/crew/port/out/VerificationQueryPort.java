package com.triagain.crew.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Crew → Verification 컨텍스트 간 조회 포트 — 오늘 인증 현황 조회에 사용 */
public interface VerificationQueryPort {

    Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate);
}