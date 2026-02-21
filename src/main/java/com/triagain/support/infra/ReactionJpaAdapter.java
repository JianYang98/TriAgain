package com.triagain.support.infra;

import com.triagain.support.domain.model.Reaction;
import com.triagain.support.port.out.ReactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReactionJpaAdapter implements ReactionRepositoryPort {

    private final ReactionJpaRepository reactionJpaRepository;

    @Override
    public Reaction save(Reaction reaction) {
        ReactionJpaEntity entity = ReactionJpaEntity.fromDomain(reaction);
        return reactionJpaRepository.save(entity).toDomain();
    }

    @Override
    public void deleteById(String id) {
        reactionJpaRepository.deleteById(id);
    }

    @Override
    public Optional<Reaction> findByVerificationIdAndUserId(String verificationId, String userId) {
        return reactionJpaRepository.findByVerificationIdAndUserId(verificationId, userId)
                .map(ReactionJpaEntity::toDomain);
    }

    @Override
    public long countByVerificationId(String verificationId) {
        return reactionJpaRepository.countByVerificationId(verificationId);
    }
}
