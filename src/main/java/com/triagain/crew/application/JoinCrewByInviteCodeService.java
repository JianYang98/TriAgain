package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.port.in.JoinCrewByInviteCodeUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JoinCrewByInviteCodeService implements JoinCrewByInviteCodeUseCase {

    private final CrewRepositoryPort crewRepositoryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;

    /** 초대코드로 크루 참여 — 공유 링크를 통해 참여할 때 사용 */
    @Override
    @Transactional
    public JoinByInviteCodeResult joinByInviteCode(JoinByInviteCodeCommand command) {
        Crew crew = crewRepositoryPort.findByInviteCode(command.inviteCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

        Crew lockedCrew = crewRepositoryPort.findByIdWithLock(crew.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        validateJoin(lockedCrew, command.userId());

        CrewMember member = lockedCrew.addMember(command.userId());
        crewRepositoryPort.save(lockedCrew);
        crewRepositoryPort.saveMember(member);

        if (lockedCrew.getStatus() == CrewStatus.ACTIVE) {
            LocalDate startDate = LocalDate.now();
            LocalDateTime deadline = startDate.plusDays(3).atTime(23, 59, 59);
            Challenge challenge = Challenge.createFirst(
                    command.userId(),
                    lockedCrew.getId(),
                    startDate,
                    deadline
            );
            challengeRepositoryPort.save(challenge);
        }

        return new JoinByInviteCodeResult(
                member.getUserId(),
                member.getCrewId(),
                member.getRole(),
                member.getJoinedAt()
        );
    }

    private void validateJoin(Crew crew, String userId) {
        if (crew.canNotJoin()) {
            if (crew.isFull()) {
                throw new BusinessException(ErrorCode.CREW_FULL);
            }
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        if (crew.isJoinDeadlinePassed()) {
            throw new BusinessException(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
        }
        if (crew.getMembers().stream().anyMatch(m -> m.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
        }
    }
}
