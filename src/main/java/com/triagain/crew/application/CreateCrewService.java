package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.CreateCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCrewService implements CreateCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    @Override
    @Transactional
    public CreateCrewResult createCrew(CreateCrewCommand command) {
        Crew crew = Crew.create(
                command.creatorId(),
                command.name(),
                command.goal(),
                command.verificationType(),
                Crew.MIN_MEMBERS,
                command.maxMembers(),
                command.startDate(),
                command.endDate(),
                command.allowLateJoin()
        );

        Crew saved = crewRepositoryPort.save(crew);
        saved.getMembers().forEach(crewRepositoryPort::saveMember);

        return new CreateCrewResult(
                saved.getId(),
                saved.getCreatorId(),
                saved.getName(),
                saved.getGoal(),
                saved.getVerificationType(),
                saved.getMaxMembers(),
                saved.getCurrentMembers(),
                saved.getStatus(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.isAllowLateJoin(),
                saved.getInviteCode(),
                saved.getCreatedAt()
        );
    }
}
