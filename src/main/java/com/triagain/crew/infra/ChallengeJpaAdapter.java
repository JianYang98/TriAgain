package com.triagain.crew.infra;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChallengeJpaAdapter implements ChallengeRepositoryPort {

	private final ChallengeJpaRepository challengeJpaRepository;

	/** 챌린지 저장 — 생성·상태 변경 시 사용 */
	@Override
	public Challenge save(Challenge challenge) {
		ChallengeJpaEntity entity = ChallengeJpaEntity.fromDomain(challenge);
		return challengeJpaRepository.save(entity).toDomain();
	}

	/** ID로 챌린지 조회 — 인증 제출 시 대상 챌린지 확인에 사용 */
	@Override
	public Optional<Challenge> findById(String id) {
		return challengeJpaRepository.findById(id)
				.map(ChallengeJpaEntity::toDomain);
	}

	/** 유저·크루·상태로 챌린지 조회 — 진행 중인 챌린지 확인에 사용 */
	@Override
	public Optional<Challenge> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status) {
		return challengeJpaRepository.findByUserIdAndCrewIdAndStatus(userId, crewId, status)
				.map(ChallengeJpaEntity::toDomain);
	}

	/** 크루의 특정 상태 챌린지 목록 조회 — 크루 상세에서 멤버별 현황 표시에 사용 */
	@Override
	public List<Challenge> findAllByCrewIdAndStatus(String crewId, ChallengeStatus status) {
		return challengeJpaRepository.findAllByCrewIdAndStatus(crewId, status).stream()
				.map(ChallengeJpaEntity::toDomain)
				.toList();
	}

	/** 마감 초과 + 미인증 챌린지 조회 — 실패 판정 스케줄러에서 사용 */
	@Override
	public List<Challenge> findExpiredWithoutVerification() {
		return challengeJpaRepository.findExpiredWithoutVerification().stream()
				.map(ChallengeJpaEntity::toDomain)
				.toList();
	}

	/** 비관적 락으로 IN_PROGRESS 챌린지 조회 — 동시 챌린지 생성 방지에 사용 */
	@Override
	public Optional<Challenge> findByUserIdAndCrewIdAndStatusWithLock(String userId, String crewId, ChallengeStatus status) {
		return challengeJpaRepository.findByUserIdAndCrewIdAndStatusWithLock(userId, crewId, status)
				.map(ChallengeJpaEntity::toDomain);
	}

	/** 유저·크루의 최대 사이클 번호 조회 — 다음 사이클 번호 결정에 사용 */
	@Override
	public int findMaxCycleNumber(String userId, String crewId) {
		return challengeJpaRepository.findMaxCycleNumber(userId, crewId);
	}

	/** 유저·크루의 SUCCESS 챌린지 수 조회 — 작심삼일 달성 횟수에 사용 */
	@Override
	public int countSuccessByUserIdAndCrewId(String userId, String crewId) {
		return challengeJpaRepository.countSuccessByUserIdAndCrewId(userId, crewId);
	}

	/** 유저·크루의 챌린지 존재 여부 확인 — ACTIVE 크루 탈퇴 시 챌린지 시작 여부 판단에 사용 */
	@Override
	public boolean existsByUserIdAndCrewId(String userId, String crewId) {
		return challengeJpaRepository.existsByUserIdAndCrewId(userId, crewId);
	}

	/** 크루의 챌린지 존재 여부 확인 — 크루 삭제 시 인증 시작 여부 판단에 사용 (crew_id 기준) */
	@Override
	public boolean existsByCrewId(String crewId) {
		return challengeJpaRepository.existsByCrewId(crewId);
	}

	/** 크루 멤버별 성공 횟수 조회 — 작심삼일 성공 카운트에 사용 */
	@Override
	public Map<String, Integer> countSuccessByCrewId(String crewId) {
		List<Object[]> results = challengeJpaRepository.countSuccessGroupByUserId(crewId);
		Map<String, Integer> map = new HashMap<>();
		for (Object[] row : results) {
			map.put((String) row[0], ((Long) row[1]).intValue());
		}
		return map;
	}

	/** 유저·크루 묶음별 SUCCESS 챌린지 수 조회 — 홈 완료 탭 배치 집계에 사용 */
	@Override
	public Map<String, Integer> findSuccessCountsByUserIdAndCrewIds(String userId, List<String> crewIds) {
		if (crewIds.isEmpty()) {
			return Map.of();
		}
		List<Object[]> results = challengeJpaRepository.countSuccessGroupByCrewId(userId, crewIds);
		Map<String, Integer> map = new HashMap<>();
		for (Object[] row : results) {
			map.put((String) row[0], ((Long) row[1]).intValue());
		}
		return map;
	}

	/** 유저·크루 묶음별 특정 상태 챌린지 조회 — 홈 목록 챌린지 진행도 배치 조회에 사용 */
	@Override
	public List<Challenge> findAllByUserIdAndCrewIdInAndStatus(
			String userId, List<String> crewIds, ChallengeStatus status) {
		if (crewIds.isEmpty()) {
			return List.of();
		}
		return challengeJpaRepository.findAllByUserIdAndCrewIdInAndStatus(userId, crewIds, status).stream()
				.map(ChallengeJpaEntity::toDomain)
				.toList();
	}

	/** IN_PROGRESS + completed_days 일치 시에만 FAILED 전환 — 만료 스케줄러 lost update 방지에 사용 */
	@Override
	public int failIfUnchanged(String id, int expectedCompletedDays) {
		return challengeJpaRepository.failIfUnchanged(id, expectedCompletedDays);
	}

	/** IN_PROGRESS/SUCCESS + completed_days 일치 시에만 1 감소 + IN_PROGRESS 복귀 — 인증 취소 역연산에 사용 */
	@Override
	public int revertCompletionIfUnchanged(String id, int expectedCompletedDays) {
		return challengeJpaRepository.revertCompletionIfUnchanged(id, expectedCompletedDays);
	}
}
