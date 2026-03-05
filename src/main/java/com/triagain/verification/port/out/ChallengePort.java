package com.triagain.verification.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ChallengePort {

    Optional<ChallengeInfo> findChallengeById(String challengeId);

    void recordCompletion(String challengeId);

    /** 유저의 활성 챌린지 조회 — 피드 myProgress 표시에 사용 */
    Optional<ActiveChallengeInfo> findActiveByUserIdAndCrewId(String userId, String crewId);

    /** 활성 챌린지 조회 또는 자동 생성 — 인증 시 챌린지 없으면 생성 */
    ChallengeInfo findOrCreateActiveChallenge(String userId, String crewId);

    /** 유저의 SUCCESS 챌린지 수 조회 — 작심삼일 달성 횟수 */
    int countCompletedChallenges(String userId, String crewId);

    record ChallengeInfo(
            String id,
            String userId,
            String crewId,
            int completedDays,
            int targetDays,
            String status,
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
