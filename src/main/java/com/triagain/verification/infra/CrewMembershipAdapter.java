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

    /** 크루 기간 조회 — 같은 트랜잭션 내 JPA L1 캐시로 중복 DB 호출 없음 */
    @Override
    public CrewPeriod getCrewPeriod(String crewId) {
        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
        return new CrewPeriod(crew.getStartDate(), crew.getEndDate());
    }

    /** 크루 인증 윈도우 정보 조회 — 업로드 세션 마감 검증에 사용 */
    @Override
    public CrewVerificationWindowInfo getCrewVerificationWindowInfo(String crewId) {
        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
        return new CrewVerificationWindowInfo(
                crew.getVerificationType().name(),
                crew.getStatus().name(),
                crew.getStartDate(),
                crew.getEndDate(),
                crew.isAllowLateJoin(),
                crew.getDeadlineTime()
        );
    }
}
