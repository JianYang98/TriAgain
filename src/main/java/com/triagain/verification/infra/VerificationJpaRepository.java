package com.triagain.verification.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface VerificationJpaRepository extends JpaRepository<VerificationJpaEntity, String> {

    boolean existsByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate);
}
