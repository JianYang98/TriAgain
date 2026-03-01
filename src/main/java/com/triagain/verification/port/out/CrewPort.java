package com.triagain.verification.port.out;

public interface CrewPort {

    /** 크루 멤버십 검증 — 미참여 시 BusinessException(CREW_ACCESS_DENIED) */
    void validateMembership(String crewId, String userId);

    /** 크루 인증방식 조회 — PHOTO 크루의 사진 필수 검증에 사용 */
    String getVerificationType(String crewId);
}
