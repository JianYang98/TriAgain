package com.triagain.moderation.infra;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.moderation.port.out.CrewPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CrewClientAdapter implements CrewPort {

    private final CrewRepositoryPort crewRepositoryPort;

    @Override
    public Optional<String> findCrewLeaderId(String crewId) {
        return crewRepositoryPort.findById(crewId)
                .flatMap(crew -> crew.getMembers().stream()
                        .filter(CrewMember::isLeader)
                        .findFirst()
                        .map(CrewMember::getUserId));
    }

    @Override
    public boolean isCrewMember(String crewId, String userId) {
        return crewRepositoryPort.findById(crewId)
                .map(crew -> crew.getMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(userId)))
                .orElse(false);
    }
}
