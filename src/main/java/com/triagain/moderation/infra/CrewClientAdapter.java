package com.triagain.moderation.infra;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.triagain.crew.port.in.CrewMembershipQueryUseCase;
import com.triagain.moderation.port.out.CrewPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CrewClientAdapter implements CrewPort {

	private final CrewMembershipQueryUseCase crewMembershipQueryUseCase;

	@Override
	public Optional<String> findCrewLeaderId(String crewId) {
		return crewMembershipQueryUseCase.findCrewLeaderId(crewId);
	}

	@Override
	public boolean isCrewMember(String crewId, String userId) {
		return crewMembershipQueryUseCase.isCrewMember(crewId, userId);
	}
}
