package com.triagain.moderation.port.out;

import java.util.Optional;

public interface CrewPort {

    Optional<String> findCrewLeaderId(String crewId);

    boolean isCrewMember(String crewId, String userId);
}
