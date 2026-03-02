package com.triagain.verification.infra;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.verification.port.out.CrewPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrewMembershipAdapter implements CrewPort {

    private final CrewRepositoryPort crewRepositoryPort;

    /** 크루 멤버십 검증 — 크루 미존재 시 404, 미참여 시 403 */
    @Override
    public void validateMembership(String crewId, String userId) {
        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        boolean isMember = crew.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));

        if (!isMember) {
            throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
        }
    }

    /** 크루 인증방식 조회 — PHOTO/TEXT 반환 */
    @Override
    public String getVerificationType(String crewId) {
        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
        return crew.getVerificationType().name();
    }
}
