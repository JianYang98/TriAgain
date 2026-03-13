package com.triagain.verification.infra;

import com.triagain.crew.port.in.ChallengeQueryUseCase;
import com.triagain.crew.port.in.ChallengeQueryUseCase.ChallengeInfoDto;
import com.triagain.verification.port.out.ChallengePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChallengeClientAdapter implements ChallengePort {

    private final ChallengeQueryUseCase challengeQueryUseCase;

    @Override
    public Optional<ChallengeInfo> findChallengeById(String challengeId) {
        return challengeQueryUseCase.findById(challengeId)
                .map(this::toChallengeInfo);
    }

    /** 챌린지 완료 기록 — ChallengeQueryUseCase에 위임 */
    @Override
    public void recordCompletion(String challengeId) {
        challengeQueryUseCase.recordCompletion(challengeId);
    }

    /** 유저의 활성 챌린지 조회 — IN_PROGRESS 상태만 반환 */
    @Override
    public Optional<ActiveChallengeInfo> findActiveByUserIdAndCrewId(String userId, String crewId) {
        return challengeQueryUseCase.findActiveByUserIdAndCrewId(userId, crewId)
                .map(this::toActiveChallengeInfo);
    }

    /** 활성 챌린지 조회 또는 자동 생성 — crew context UseCase에 위임 */
    @Override
    public ChallengeInfo findOrCreateActiveChallenge(String userId, String crewId) {
        ChallengeInfoDto dto = challengeQueryUseCase.findOrCreateActive(userId, crewId);
        return toChallengeInfo(dto);
    }

    /** 유저의 SUCCESS 챌린지 수 조회 — ChallengeQueryUseCase에 위임 */
    @Override
    public int countCompletedChallenges(String userId, String crewId) {
        return challengeQueryUseCase.countCompletedChallenges(userId, crewId);
    }

    private ChallengeInfo toChallengeInfo(ChallengeInfoDto dto) {
        return new ChallengeInfo(
                dto.id(),
                dto.userId(),
                dto.crewId(),
                dto.completedDays(),
                dto.targetDays(),
                dto.status(),
                dto.startDate(),
                dto.deadline()
        );
    }

    private ActiveChallengeInfo toActiveChallengeInfo(ChallengeInfoDto dto) {
        return new ActiveChallengeInfo(
                dto.id(),
                dto.status(),
                dto.completedDays(),
                dto.targetDays(),
                dto.deadline()
        );
    }
}
