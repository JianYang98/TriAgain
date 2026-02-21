package com.triagain.verification.infra;

import com.triagain.crew.domain.model.Challenge;
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
