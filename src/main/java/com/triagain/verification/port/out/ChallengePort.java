package com.triagain.verification.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ChallengePort {

    Optional<ChallengeInfo> findChallengeById(String challengeId);

    void recordCompletion(String challengeId);

    /** 유저의 활성 챌린지 조회 — 피드 myProgress 표시에 사용 */
    Optional<ActiveChallengeInfo> findActiveByUserIdAndCrewId(String userId, String crewId);

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

    record ActiveChallengeInfo(
            String id,
            String status,
            int completedDays,
            int targetDays
    ) {
    }
}
