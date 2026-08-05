package com.triagain.crew.port.in;

import java.time.LocalDateTime;

import com.triagain.crew.domain.vo.CrewRole;

public interface JoinCrewUseCase {

	/** 크루 가입  */
	JoinCrewResult joinCrew(JoinCrewCommand command);

	record JoinCrewCommand(String userId, String crewId) {
	}

	record JoinCrewResult(String userId, String crewId, CrewRole role, int currentMembers, LocalDateTime joinedAt) {
	}
}
