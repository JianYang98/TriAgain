package com.triagain.verification.port.out;

import com.triagain.verification.domain.model.Verification;

import java.time.LocalDate;
import java.util.Optional;

public interface VerificationRepositoryPort {

    Verification save(Verification verification);

    Optional<Verification> findById(String id);

    boolean existsByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate);
}
