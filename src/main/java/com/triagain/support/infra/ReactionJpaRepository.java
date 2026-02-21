package com.triagain.support.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReactionJpaRepository extends JpaRepository<ReactionJpaEntity, String> {

    Optional<ReactionJpaEntity> findByVerificationIdAndUserId(String verificationId, String userId);

    long countByVerificationId(String verificationId);
}
