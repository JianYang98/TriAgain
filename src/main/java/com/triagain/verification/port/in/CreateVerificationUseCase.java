package com.triagain.verification.port.in;

import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface CreateVerificationUseCase {

    VerificationResult createVerification(CreateVerificationCommand command);

    record CreateVerificationCommand(
            String userId,
            String challengeId,
            Long uploadSessionId,
            String imageUrl,
            String textContent
    ) {
    }

    record VerificationResult(
            String verificationId,
            String challengeId,
            String userId,
            String crewId,
            String imageUrl,
            String textContent,
            VerificationStatus status,
            ReviewStatus reviewStatus,
            int reportCount,
            LocalDate targetDate,
            LocalDateTime createdAt
    ) {
    }
}
