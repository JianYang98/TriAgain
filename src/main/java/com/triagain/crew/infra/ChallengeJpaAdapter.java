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

    /** 챌린지 저장 — 생성·상태 변경 시 사용 */
    @Override
    public Challenge save(Challenge challenge) {
        ChallengeJpaEntity entity = ChallengeJpaEntity.fromDomain(challenge);
        return challengeJpaRepository.save(entity).toDomain();
    }

    /** ID로 챌린지 조회 — 인증 제출 시 대상 챌린지 확인에 사용 */
    @Override
    public Optional<Challenge> findById(String id) {
        return challengeJpaRepository.findById(id)
                .map(ChallengeJpaEntity::toDomain);
    }

    /** 유저·크루·상태로 챌린지 조회 — 진행 중인 챌린지 확인에 사용 */
    @Override
    public Optional<Challenge> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status) {
        return challengeJpaRepository.findByUserIdAndCrewIdAndStatus(userId, crewId, status)
                .map(ChallengeJpaEntity::toDomain);
    }
}
