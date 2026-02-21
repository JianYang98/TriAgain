package com.triagain.crew.infra;

import com.triagain.crew.domain.vo.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeJpaRepository extends JpaRepository<ChallengeJpaEntity, String> {

    Optional<ChallengeJpaEntity> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status);
}
