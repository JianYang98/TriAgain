package com.triagain.verification.infra;

import com.triagain.crew.port.in.CrewMembershipQueryUseCase;
import com.triagain.crew.port.in.CrewMembershipQueryUseCase.CrewPeriodDto;
import com.triagain.crew.port.in.CrewMembershipQueryUseCase.CrewVerificationWindowDto;
import com.triagain.verification.port.out.CrewPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrewMembershipAdapter implements CrewPort {

    private final CrewMembershipQueryUseCase crewMembershipQueryUseCase;

    /** 크루 멤버십 검증 — 크루 미존재 시 404, 미참여 시 403 */
    @Override
    public void validateMembership(String crewId, String userId) {
        crewMembershipQueryUseCase.validateMembership(crewId, userId);
    }

    /** 크루 인증방식 조회 — PHOTO/TEXT 반환 */
    @Override
    public String getVerificationType(String crewId) {
        return crewMembershipQueryUseCase.getVerificationType(crewId);
    }

    /** 크루 기간 조회 — 같은 트랜잭션 내 JPA L1 캐시로 중복 DB 호출 없음 */
    @Override
    public CrewPeriod getCrewPeriod(String crewId) {
        CrewPeriodDto dto = crewMembershipQueryUseCase.getCrewPeriod(crewId);
        return new CrewPeriod(dto.startDate(), dto.endDate());
    }

    /** 크루 인증 윈도우 정보 조회 — 업로드 세션 마감 검증에 사용 */
    @Override
    public CrewVerificationWindowInfo getCrewVerificationWindowInfo(String crewId) {
        CrewVerificationWindowDto dto = crewMembershipQueryUseCase.getCrewVerificationWindowInfo(crewId);
        return new CrewVerificationWindowInfo(
                dto.verificationType(),
                dto.status(),
                dto.startDate(),
                dto.endDate(),
                dto.allowLateJoin(),
                dto.deadlineTime()
        );
    }
}
