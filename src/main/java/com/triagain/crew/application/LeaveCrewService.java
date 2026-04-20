package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.LeaveCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveCrewService implements LeaveCrewUseCase {


	private final CrewRepositoryPort crewRepositoryPort;
	private final ChallengeRepositoryPort challengeRepositoryPort;
	private final CrewLockProperties lockProperties;
	private final TransactionTemplate txTemplate;

	/** 크루 탈퇴 — RECRUITING 무조건 가능, ACTIVE는 챌린지 미시작 멤버만 가능 */
	@Override
	public void leaveCrew(String crewId, String userId) {
		if (lockProperties.isPessimistic()) {
			txTemplate.executeWithoutResult(
				status -> doLeavePessimistic(crewId, userId));
			return;
		}
		leaveWithOptimisticRetry(crewId, userId);
	}

	private void leaveWithOptimisticRetry(
			String crewId, String userId) {
		for (int attempt = 1; attempt <= lockProperties.getMaxRetry(); attempt++) {
			Boolean success = txTemplate.execute(
				status -> doLeaveOptimistic(crewId, userId));
			if (Boolean.TRUE.equals(success)) {
				return;
			}
		}
		throw new BusinessException(ErrorCode.CREW_JOIN_CONFLICT);
	}

	private void doLeavePessimistic(String crewId, String userId) {
		Crew crew = crewRepositoryPort.findByIdWithLock(crewId)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));
		doLeave(crew, crewId, userId, true);
	}

	private Boolean doLeaveOptimistic(
			String crewId, String userId) {
		Crew crew = crewRepositoryPort.findById(crewId)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));

		boolean hasStartedChallenge = challengeRepositoryPort
			.existsByUserIdAndCrewId(userId, crewId);
		crew.removeMember(userId, hasStartedChallenge);

		int updated = crewRepositoryPort
			.updateCurrentMembersWithVersion(
				crew.getId(), crew.getCurrentMembers(),
				crew.getVersion());
		if (updated == 0) {
			return false;
		}
		crewRepositoryPort
			.deleteMemberByCrewIdAndUserId(crewId, userId);

		if (crew.getCurrentMembers() == 0) {
			crewRepositoryPort.deleteById(crewId);
		}
		return true;
	}

	private void doLeave(Crew crew, String crewId,
			String userId, boolean useSave) {
		boolean hasStartedChallenge = challengeRepositoryPort
			.existsByUserIdAndCrewId(userId, crewId);
		crew.removeMember(userId, hasStartedChallenge);
		crewRepositoryPort.save(crew);
		crewRepositoryPort
			.deleteMemberByCrewIdAndUserId(crewId, userId);

		if (crew.getCurrentMembers() == 0) {
			crewRepositoryPort.deleteById(crewId);
		}
	}
}
