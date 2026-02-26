package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.GetCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetCrewService implements GetCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    /** 크루 상세 조회 — 크루 멤버가 상세 화면을 볼 때 사용 */
    @Override
    @Transactional(readOnly = true)
    public CrewDetailResult getCrew(String crewId, String userId) {
        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        boolean isMember = crew.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
        }

        List<MemberResult> members = crew.getMembers().stream()
                .map(m -> new MemberResult(m.getUserId(), m.getRole(), m.getJoinedAt()))
                .toList();

        return new CrewDetailResult(
                crew.getId(),
                crew.getCreatorId(),
                crew.getName(),
                crew.getGoal(),
                crew.getVerificationType(),
                crew.getMaxMembers(),
                crew.getCurrentMembers(),
                crew.getStatus(),
                crew.getStartDate(),
                crew.getEndDate(),
                crew.isAllowLateJoin(),
                crew.getInviteCode(),
                crew.getCreatedAt(),
                members
        );
    }
}
