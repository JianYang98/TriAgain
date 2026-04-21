package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.in.DeleteCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeleteCrewService implements DeleteCrewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;

	/** 크루 삭제 — RECRUITING + LEADER + 멤버 1명(본인만)일 때 hard delete */
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

		crew.validateDeletable();

		crewRepositoryPort.deleteById(crewId);
	}
}
