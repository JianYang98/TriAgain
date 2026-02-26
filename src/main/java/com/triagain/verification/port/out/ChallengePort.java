package com.triagain.verification.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ChallengePort {

    Optional<ChallengeInfo> findChallengeById(String challengeId);

    void recordCompletion(String challengeId);

    record ChallengeInfo(
            String id,
            String userId,
            String crewId,
            int completedDays,
            int targetDays,
            LocalDate startDate,
            LocalDateTime deadline
    ) {
    }
}
