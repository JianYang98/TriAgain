package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.LeaveCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveCrewService implements LeaveCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    /** 크루 탈퇴 — RECRUITING 상태에서 MEMBER만 가능 */
    @Override
    @Transactional
    public void leaveCrew(String crewId, String userId) {
        Crew crew = crewRepositoryPort.findByIdWithLock(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        crew.removeMember(userId);
        crewRepositoryPort.save(crew);
        crewRepositoryPort.deleteMemberByCrewIdAndUserId(crewId, userId);
    }
}
