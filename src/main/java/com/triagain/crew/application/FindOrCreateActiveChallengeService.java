package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.common.domain.DeadlinePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FindOrCreateActiveChallengeService {

    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final CrewRepositoryPort crewRepositoryPort;

    /** 활성 챌린지 조회 또는 생성 — 인증 시 챌린지가 없으면 자동 생성 */
    @Transactional
    public Challenge findOrCreate(String userId, String crewId) {
        Optional<Challenge> existing = challengeRepositoryPort
                .findByUserIdAndCrewIdAndStatusWithLock(userId, crewId, ChallengeStatus.IN_PROGRESS);
        if (existing.isPresent()) {
            return existing.get();
        }

        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        validateCrewActive(crew);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today;
        LocalDate deadlineDate = startDate.plusDays(3);
        if (deadlineDate.isAfter(crew.getEndDate())) {
            deadlineDate = crew.getEndDate();
        }
        LocalDateTime deadline = deadlineDate.atTime(crew.getDeadlineTime());

        int maxCycle = challengeRepositoryPort.findMaxCycleNumber(userId, crewId);
        Challenge challenge;
        if (maxCycle == 0) {
            challenge = Challenge.createFirst(userId, crewId, startDate, deadline);
        } else {
            challenge = Challenge.createNext(userId, crewId, maxCycle, startDate, deadline);
        }

        try {
            return challengeRepositoryPort.save(challenge);
        } catch (DataIntegrityViolationException e) {
            return challengeRepositoryPort
                    .findByUserIdAndCrewIdAndStatusWithLock(userId, crewId, ChallengeStatus.IN_PROGRESS)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        }
    }

    private void validateCrewActive(Crew crew) {
        if (crew.getStatus() != CrewStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CREW_NOT_ACTIVE);
        }
        if (LocalDate.now().isAfter(crew.getEndDate())) {
            throw new BusinessException(ErrorCode.CREW_PERIOD_ENDED);
        }
        LocalDateTime todayDeadline = DeadlinePolicy.todayDeadline(crew.getDeadlineTime());
        if (!DeadlinePolicy.isWithinDeadline(LocalDateTime.now(), todayDeadline)) {
            throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
        }
    }
}
