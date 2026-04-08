package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.LeaveCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveCrewService implements LeaveCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;

    /** 크루 탈퇴 — RECRUITING 무조건 가능, ACTIVE는 챌린지 미시작 멤버만 가능 */
    @Override
    @Transactional
    public void leaveCrew(String crewId, String userId) {
        Crew crew = crewRepositoryPort.findByIdWithLock(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        boolean hasStartedChallenge = challengeRepositoryPort.existsByUserIdAndCrewId(userId, crewId);
        crew.removeMember(userId, hasStartedChallenge);
        crewRepositoryPort.save(crew);
        crewRepositoryPort.deleteMemberByCrewIdAndUserId(crewId, userId);

        // 빈 크루 자동 정리 — 방어 로직 (회원탈퇴 LEADER 자동 위임 등으로 도달 가능)
        if (crew.getCurrentMembers() == 0) {
            crewRepositoryPort.deleteById(crewId);
        }
    }
}
