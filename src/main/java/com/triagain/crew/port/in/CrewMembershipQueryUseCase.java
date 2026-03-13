package com.triagain.crew.port.in;

import java.time.LocalDate;
import java.time.LocalTime;

public interface CrewMembershipQueryUseCase {

    /** 크루 멤버십 검증 — 미참여 시 BusinessException(CREW_ACCESS_DENIED) */
    void validateMembership(String crewId, String userId);

    /** 크루 인증방식 조회 — PHOTO/TEXT 반환 */
    String getVerificationType(String crewId);

    /** 크루 기간 조회 — 인증 날짜 범위 제한에 사용 */
    CrewPeriodDto getCrewPeriod(String crewId);

    /** 크루 인증 윈도우 정보 조회 — 업로드 세션 발급 가능 여부 판단에 사용 */
    CrewVerificationWindowDto getCrewVerificationWindowInfo(String crewId);

    record CrewPeriodDto(LocalDate startDate, LocalDate endDate) {}

    record CrewVerificationWindowDto(
            String verificationType,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            boolean allowLateJoin,
            LocalTime deadlineTime
    ) {}
}
