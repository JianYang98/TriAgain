package com.triagain.verification.port.out;

import java.time.LocalDate;

public interface CrewPort {

    /** 크루 멤버십 검증 — 미참여 시 BusinessException(CREW_ACCESS_DENIED) */
    void validateMembership(String crewId, String userId);

    /** 크루 인증방식 조회 — PHOTO 크루의 사진 필수 검증에 사용 */
    String getVerificationType(String crewId);

    /** 크루 기간 조회 — 인증 날짜 범위 제한에 사용 */
    CrewPeriod getCrewPeriod(String crewId);

    record CrewPeriod(LocalDate startDate, LocalDate endDate) {
    }
}
