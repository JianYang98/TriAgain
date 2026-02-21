package com.triagain.crew.port.out;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;

import java.util.Optional;

public interface ChallengeRepositoryPort {

    Challenge save(Challenge challenge);

    Optional<Challenge> findById(String id);

    Optional<Challenge> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status);
}
