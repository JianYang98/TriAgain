package com.triagain.support.port.out;

import com.triagain.support.domain.model.Reaction;

import java.util.Optional;

public interface ReactionRepositoryPort {

    Reaction save(Reaction reaction);

    void deleteById(String id);

    Optional<Reaction> findByVerificationIdAndUserId(String verificationId, String userId);

    long countByVerificationId(String verificationId);
}
