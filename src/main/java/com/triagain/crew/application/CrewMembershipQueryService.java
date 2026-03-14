package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.in.CrewMembershipQueryUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CrewMembershipQueryService implements CrewMembershipQueryUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    @Override
    public void validateMembership(String crewId, String userId) {
        Crew crew = findCrewOrThrow(crewId);

        boolean isMember = crew.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));

        if (!isMember) {
            throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
        }
    }

    @Override
    public String getVerificationType(String crewId) {
        Crew crew = findCrewOrThrow(crewId);
        return crew.getVerificationType().name();
    }

    @Override
    public CrewPeriodDto getCrewPeriod(String crewId) {
        Crew crew = findCrewOrThrow(crewId);
        return new CrewPeriodDto(crew.getStartDate(), crew.getEndDate());
    }

    @Override
    public CrewVerificationWindowDto getCrewVerificationWindowInfo(String crewId) {
        Crew crew = findCrewOrThrow(crewId);
        return new CrewVerificationWindowDto(
                crew.getVerificationType().name(),
                crew.getStatus().name(),
                crew.getStartDate(),
                crew.getEndDate(),
                crew.isAllowLateJoin(),
                crew.getDeadlineTime()
        );
    }

    @Override
    public Optional<String> findCrewLeaderId(String crewId) {
        Crew crew = findCrewOrThrow(crewId);
        return crew.getMembers().stream()
                .filter(CrewMember::isLeader)
                .findFirst()
                .map(CrewMember::getUserId);
    }

    @Override
    public boolean isCrewMember(String crewId, String userId) {
        return crewRepositoryPort.findById(crewId)
                .map(crew -> crew.getMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(userId)))
                .orElse(false);
    }

    private Crew findCrewOrThrow(String crewId) {
        return crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
    }
}
