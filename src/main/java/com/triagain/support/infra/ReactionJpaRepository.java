package com.triagain.support.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionJpaRepository extends JpaRepository<ReactionJpaEntity, String> {

	Optional<ReactionJpaEntity> findByVerificationIdAndUserId(String verificationId, String userId);

	long countByVerificationId(String verificationId);
}
