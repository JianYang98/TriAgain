
package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.infra.CrewLockProperties;
import com.triagain.crew.port.in.JoinCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JoinCrewService implements JoinCrewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;
	private final CrewLockProperties lockProperties;
	private final TransactionTemplate txTemplate;

	/** 크루 가입 — 설정에 따라 비관적/낙관적 락 전략 분기 */
	@Override
	public JoinCrewResult joinCrew(JoinCrewCommand command) {
		if (lockProperties.isPessimistic()) {
			return txTemplate.execute(
				status -> doJoinPessimistic(command));
		}
		return joinWithOptimisticRetry(command);
	}

	private JoinCrewResult joinWithOptimisticRetry(
			JoinCrewCommand command) {
		for (int attempt = 1; attempt <= lockProperties.getMaxRetry(); attempt++) {
			JoinCrewResult result = txTemplate.execute(
				status -> doJoinOptimistic(command));
			if (result != null) {
				return result;
			}
		}
		throw new BusinessException(ErrorCode.CREW_JOIN_CONFLICT);
	}

	private JoinCrewResult doJoinPessimistic(JoinCrewCommand command) {
		Crew crew = crewRepositoryPort.findByIdWithLock(command.crewId())
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));
		return doJoin(crew, command.userId());
	}

	private JoinCrewResult doJoinOptimistic(JoinCrewCommand command) {
		Crew crew = crewRepositoryPort.findById(command.crewId())
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));

		if (!crew.isPublic()) {
			throw new BusinessException(ErrorCode.CREW_NOT_PUBLIC);
		}

		CrewMember member = crew.addMember(command.userId());
		int updated = crewRepositoryPort
			.updateCurrentMembersWithVersion(
				crew.getId(), crew.getCurrentMembers(),
				crew.getVersion());
		if (updated == 0) {
			return null;
		}
		crewRepositoryPort.saveMember(member);

		return new JoinCrewResult(
			member.getUserId(), member.getCrewId(),
			member.getRole(), crew.getCurrentMembers(),
			member.getJoinedAt());
	}

	private JoinCrewResult doJoin(Crew crew, String userId) {
		if (!crew.isPublic()) {
			throw new BusinessException(ErrorCode.CREW_NOT_PUBLIC);
		}
		CrewMember member = crew.addMember(userId);
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(member);
		return new JoinCrewResult(
			member.getUserId(), member.getCrewId(),
			member.getRole(), crew.getCurrentMembers(),
			member.getJoinedAt());
	}
}
