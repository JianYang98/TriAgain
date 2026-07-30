package com.triagain.support.port.out;

import java.util.Optional;

import com.triagain.support.domain.model.Reaction;

public interface ReactionRepositoryPort {

	Reaction save(Reaction reaction);

	void deleteById(String id);

	Optional<Reaction> findByVerificationIdAndUserId(String verificationId, String userId);

	long countByVerificationId(String verificationId);
}
