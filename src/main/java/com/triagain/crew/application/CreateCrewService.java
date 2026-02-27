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

    /** 크루 생성 — 크루장을 리더 멤버로 자동 등록 */
    @Override
    @Transactional
    public CreateCrewResult createCrew(CreateCrewCommand command) {

        // 크루 생성 및 , 리더 지정
        Crew crew = Crew.create(
                command.creatorId(),
                command.name(),
                command.goal(),
                command.verificationType(),
                command.maxMembers(),
                command.startDate(),
                command.endDate(),
                command.allowLateJoin()
        );

        Crew saved = crewRepositoryPort.save(crew);
        crew.getMembers().forEach(crewRepositoryPort::saveMember); // 리더 멤버로 추가

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
