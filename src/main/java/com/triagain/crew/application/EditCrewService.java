package com.triagain.crew.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.in.EditCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditCrewService implements EditCrewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;

	/** 크루 정보 수정 — RECRUITING 상태에서 LEADER만 가능 */
	@Override
	@Transactional
	public EditCrewResult editCrew(EditCrewCommand command) {
		validateFields(command);

		Crew crew = crewRepositoryPort.findById(command.crewId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

		CrewMember member = crew.findMemberByUserId(command.userId());
		if (!member.isLeader()) {
			throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
		}

		crew.update(command.name(), command.goal(), command.verificationContent(),
				command.category(), command.visibility());

		Crew saved = crewRepositoryPort.save(crew);

		return new EditCrewResult(
				saved.getId(),
				saved.getCreatorId(),
				saved.getName(),
				saved.getGoal(),
				saved.getVerificationContent(),
				saved.getVerificationType(),
				saved.getMaxMembers(),
				saved.getCurrentMembers(),
				saved.getStatus(),
				saved.getStartDate(),
				saved.getEndDate(),
				saved.isAllowLateJoin(),
				saved.getInviteCode(),
				saved.getCreatedAt(),
				saved.getDeadlineTime(),
				saved.getCategory(),
				saved.getVisibility()
		);
	}

	/** 수정 요청 필드 검증 — 모두 null이거나 빈문자열/공백만 있으면 예외 */
	private void validateFields(EditCrewCommand command) {
		if (command.name() == null && command.goal() == null && command.verificationContent() == null
				&& command.category() == null && command.visibility() == null) {
			throw new BusinessException(ErrorCode.CREW_NO_FIELDS_TO_UPDATE);
		}
		validateNotBlank(command.name());
		validateNotBlank(command.goal());
		validateNotBlank(command.verificationContent());
	}

	private void validateNotBlank(String value) {
		if (value != null && value.isBlank()) {
			throw new BusinessException(ErrorCode.CREW_INVALID_FIELD_VALUE);
		}
	}
}
