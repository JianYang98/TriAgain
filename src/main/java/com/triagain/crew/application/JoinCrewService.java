package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.in.JoinCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JoinCrewService implements JoinCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    @Override
    @Transactional
    public JoinCrewResult joinCrew(JoinCrewCommand command) {
        Crew crew = crewRepositoryPort.findByIdWithLock(command.crewId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        validateJoin(crew, command.userId());

        CrewMember member = crew.addMember(command.userId());
        crewRepositoryPort.save(crew);
        crewRepositoryPort.saveMember(member);

        return new JoinCrewResult(
                member.getId(),
                member.getUserId(),
                member.getCrewId(),
                member.getRole(),
                member.getJoinedAt()
        );
    }

    private void validateJoin(Crew crew, String userId) {
        if (!crew.canJoin()) {
            if (crew.isFull()) {
                throw new BusinessException(ErrorCode.CREW_FULL);
            }
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        if (crew.getMembers().stream().anyMatch(m -> m.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
        }
    }
}
