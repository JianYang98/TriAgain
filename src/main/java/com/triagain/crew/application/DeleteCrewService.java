package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.port.in.DeleteCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeleteCrewService implements DeleteCrewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;
	private final ChallengeRepositoryPort challengeRepositoryPort;

	/** 크루 삭제 — RECRUITING 또는 ACTIVE(인증 전) + LEADER + 멤버 1명일 때 FK-safe hard delete */
	@Override
	@Transactional
	public void deleteCrew(String crewId, String userId) {
		Crew crew = crewRepositoryPort.findByIdWithLock(crewId)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));

		CrewMember member = crew.findMemberByUserId(userId);
		if (!member.isLeader()) {
			throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
		}

		// RECRUITING은 challenges 유무와 무관하게 삭제 가능 — ACTIVE일 때만 DB 조회
		boolean started = crew.getStatus() == CrewStatus.ACTIVE
				&& challengeRepositoryPort.existsByCrewId(crewId);
		crew.validateDeletable(started);

		crewRepositoryPort.deleteCrewWithAssociations(crewId);
	}
}
