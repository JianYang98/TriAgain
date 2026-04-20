package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.infra.CrewLockProperties;
import com.triagain.crew.port.in.DeleteCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeleteCrewService implements DeleteCrewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;
	private final CrewLockProperties lockProperties;

	/** 크루 삭제 — RECRUITING + LEADER + 멤버 1명(본인만)일 때 hard delete */
	@Override
	@Transactional
	public void deleteCrew(String crewId, String userId) {
		Crew crew = findCrew(crewId);

		CrewMember member = crew.findMemberByUserId(userId);
		if (!member.isLeader()) {
			throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
		}

		crew.validateDeletable();

		crewRepositoryPort.deleteById(crewId);
	}

	private Crew findCrew(String crewId) {
		return (lockProperties.isPessimistic()
			? crewRepositoryPort.findByIdWithLock(crewId)
			: crewRepositoryPort.findById(crewId))
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));
	}
}
