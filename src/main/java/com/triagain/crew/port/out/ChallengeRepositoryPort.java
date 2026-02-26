package com.triagain.crew.port.out;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;

import java.util.Optional;

public interface ChallengeRepositoryPort {

    /** 챌린지 저장 — 생성·상태 변경 시 사용 */
    Challenge save(Challenge challenge);

    /** ID로 챌린지 조회 — 인증 제출 시 대상 챌린지 확인에 사용 */
    Optional<Challenge> findById(String id);

    /** 유저·크루·상태로 챌린지 조회 — 진행 중인 챌린지 확인에 사용 */
    Optional<Challenge> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status);
}
