package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewRole;

import java.time.LocalDateTime;

public interface JoinCrewUseCase {

    /** 크루 가입  */
    JoinCrewResult joinCrew(JoinCrewCommand command);

    record JoinCrewCommand(String userId, String crewId) {
    }

    record JoinCrewResult(String userId, String crewId, CrewRole role, LocalDateTime joinedAt) {
    }
}

