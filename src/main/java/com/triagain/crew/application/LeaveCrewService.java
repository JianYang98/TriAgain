package com.triagain.crew.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.LeaveCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveCrewService implements LeaveCrewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;
	private final ChallengeRepositoryPort challengeRepositoryPort;

	/** 크루 탈퇴 — 항상 비관적 락 (빈도 낮고 실패 시 UX 불량) */
	@Override
	@Transactional
	public void leaveCrew(String crewId, String userId) {
		Crew crew = crewRepositoryPort.findByIdWithLock(crewId)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));

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
