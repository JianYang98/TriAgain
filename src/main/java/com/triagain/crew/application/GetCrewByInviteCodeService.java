package com.triagain.crew.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.UserPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCrewByInviteCodeService implements GetCrewByInviteCodeUseCase {

	private final CrewRepositoryPort crewRepositoryPort;
	private final UserPort userPort;

	/** 초대코드로 크루 미리보기 — 비멤버가 가입 전 크루 정보를 확인할 때 사용 */
	@Override
	@Transactional(readOnly = true)
	public CrewInvitePreviewResult getByInviteCode(String inviteCode, String userId) {
		Crew crew = crewRepositoryPort.findByInviteCode(inviteCode)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));
		return CrewPreviewAssembler.toPreviewResult(crew, userId, userPort);
	}
}
