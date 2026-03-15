package com.triagain.crew.port.in;

public interface DeleteCrewUseCase {

    /** 크루 삭제 — RECRUITING 상태에서 LEADER만, 멤버가 본인뿐일 때 가능 */
    void deleteCrew(String crewId, String userId);
}
