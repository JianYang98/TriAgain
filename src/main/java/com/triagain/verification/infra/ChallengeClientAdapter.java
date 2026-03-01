package com.triagain.verification.infra;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.verification.port.out.ChallengePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChallengeClientAdapter implements ChallengePort {

    private final ChallengeRepositoryPort challengeRepositoryPort;

    @Override
    public Optional<ChallengeInfo> findChallengeById(String challengeId) {
        return challengeRepositoryPort.findById(challengeId)
                .map(this::toChallengeInfo);
    }

    /** 챌린지 완료 기록 — 같은 트랜잭션 내 findChallengeById 호출 시 JPA 1차 캐시로 DB 재조회 없음 */
    @Override
    public void recordCompletion(String challengeId) {
        Challenge challenge = challengeRepositoryPort.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        challenge.recordCompletion();
        challengeRepositoryPort.save(challenge);
    }

    /** 유저의 활성 챌린지 조회 — IN_PROGRESS 상태만 반환 */
    @Override
    public Optional<ActiveChallengeInfo> findActiveByUserIdAndCrewId(String userId, String crewId) {
        return challengeRepositoryPort.findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS)
                .map(this::toActiveChallengeInfo);
    }

    private ActiveChallengeInfo toActiveChallengeInfo(Challenge challenge) {
        return new ActiveChallengeInfo(
                challenge.getId(),
                challenge.getStatus().name(),
                challenge.getCompletedDays(),
                challenge.getTargetDays()
        );
    }

    private ChallengeInfo toChallengeInfo(Challenge challenge) {
        return new ChallengeInfo(
                challenge.getId(),
                challenge.getUserId(),
                challenge.getCrewId(),
                challenge.getCompletedDays(),
                challenge.getTargetDays(),
                challenge.getStartDate(),
                challenge.getDeadline()
        );
    }
}
