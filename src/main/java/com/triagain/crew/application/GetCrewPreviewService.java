package com.triagain.crew.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase.CrewInvitePreviewResult;
import com.triagain.crew.port.in.GetCrewPreviewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.UserPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCrewPreviewService implements GetCrewPreviewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;
	private final UserPort userPort;

	/** 크루 ID로 공개 크루 미리보기 — PUBLIC 크루만 허용 */
	@Override
	@Transactional(readOnly = true)
	public CrewInvitePreviewResult getCrewPreview(String crewId, String userId) {
		Crew crew = crewRepositoryPort.findById(crewId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
		if (!crew.isPublic()) {
			throw new BusinessException(ErrorCode.CREW_NOT_PUBLIC);
		}
		return CrewPreviewAssembler.toPreviewResult(crew, userId, userPort);
	}
}
