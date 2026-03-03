package com.triagain.crew.port.out;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChallengeRepositoryPort {

    /** 챌린지 저장 — 생성·상태 변경 시 사용 */
    Challenge save(Challenge challenge);

    /** ID로 챌린지 조회 — 인증 제출 시 대상 챌린지 확인에 사용 */
    Optional<Challenge> findById(String id);

    /** 유저·크루·상태로 챌린지 조회 — 진행 중인 챌린지 확인에 사용 */
    Optional<Challenge> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status);

    /** 크루의 특정 상태 챌린지 목록 조회 — 크루 상세에서 멤버별 현황 표시에 사용 */
    List<Challenge> findAllByCrewIdAndStatus(String crewId, ChallengeStatus status);

    /** 크루 멤버별 성공 횟수 조회 — 작심삼일 성공 카운트에 사용 */
    Map<String, Integer> countSuccessByCrewId(String crewId);

    /** 마감 초과 + 미인증 챌린지 조회 — 실패 판정 스케줄러에서 사용 */
    List<Challenge> findExpiredWithoutVerification();
}
