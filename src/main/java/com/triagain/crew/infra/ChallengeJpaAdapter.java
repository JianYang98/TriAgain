package com.triagain.crew.infra;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChallengeJpaAdapter implements ChallengeRepositoryPort {

    private final ChallengeJpaRepository challengeJpaRepository;

    @Override
    public Challenge save(Challenge challenge) {
        ChallengeJpaEntity entity = ChallengeJpaEntity.fromDomain(challenge);
        return challengeJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Challenge> findById(String id) {
        return challengeJpaRepository.findById(id)
                .map(ChallengeJpaEntity::toDomain);
    }

    @Override
    public Optional<Challenge> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status) {
        return challengeJpaRepository.findByUserIdAndCrewIdAndStatus(userId, crewId, status)
                .map(ChallengeJpaEntity::toDomain);
    }
}
