package com.triagain.verification.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 오늘 인증 완료 크루 배치 조회 — Crew Context에서 호출 */
public interface CheckTodayVerificationUseCase {

    Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate);
}