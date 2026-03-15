package com.triagain.crew.port.in;

public interface LeaveCrewUseCase {

    /** 크루 탈퇴 — RECRUITING 상태에서 MEMBER만 가능, LEADER 불가 */
    void leaveCrew(String crewId, String userId);
}
