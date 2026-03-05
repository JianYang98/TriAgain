package com.triagain.verification.port.out;

import com.triagain.verification.domain.model.Verification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VerificationRepositoryPort {

    Verification save(Verification verification);

    Optional<Verification> findById(String id);

    boolean existsByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate);

    /** APPROVED 인증 날짜 조회 — 크루 기간 범위 내, ASC 정렬 */
    List<LocalDate> findApprovedDatesByUserIdAndCrewId(
            String userId, String crewId, LocalDate startDate, LocalDate endDate);
}
