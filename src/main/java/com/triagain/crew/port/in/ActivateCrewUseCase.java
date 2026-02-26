package com.triagain.crew.port.in;

public interface ActivateCrewUseCase {

    /** 크루 활성화 — 시작일 도래 시 스케줄러가 호출 */
    void activateCrew(String crewId, String requesterId);
}
