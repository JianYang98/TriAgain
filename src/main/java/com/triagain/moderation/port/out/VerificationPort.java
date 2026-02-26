package com.triagain.moderation.port.out;

import java.time.LocalDate;
import java.util.Optional;

public interface VerificationPort {

    void hideVerification(String verificationId);

    void rejectVerification(String verificationId);

    void approveVerification(String verificationId);

    int incrementReportCount(String verificationId);

    Optional<VerificationInfo> findVerificationById(String verificationId);

    record VerificationInfo(
            String id,
            String challengeId,
            String userId,
            String crewId,
            int reportCount,
            LocalDate targetDate
    ) {
    }
}
