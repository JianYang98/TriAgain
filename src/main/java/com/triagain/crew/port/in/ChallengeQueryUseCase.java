package com.triagain.crew.port.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ChallengeQueryUseCase {

    /** ID로 챌린지 조회 — Verification Context에서 인증 대상 챌린지 확인에 사용 */
    Optional<ChallengeInfoDto> findById(String challengeId);

    /** 유저의 활성 챌린지 조회 — IN_PROGRESS 상태만 반환 */
    Optional<ChallengeInfoDto> findActiveByUserIdAndCrewId(String userId, String crewId);

    /** 활성 챌린지 조회 또는 자동 생성 — 인증 시 챌린지가 없으면 생성 */
    ChallengeInfoDto findOrCreateActive(String userId, String crewId);

    /** 인증 완료 기록 — completedDays 증가 + SUCCESS 전환 */
    void recordCompletion(String challengeId);

    /** 유저의 SUCCESS 챌린지 수 조회 — 작심삼일 달성 횟수 */
    int countCompletedChallenges(String userId, String crewId);

    record ChallengeInfoDto(
            String id,
            String userId,
            String crewId,
            int completedDays,
            int targetDays,
            int cycleNumber,
            String status,
            LocalDate startDate,
            LocalDateTime deadline,
            LocalDateTime createdAt
    ) {}
}
