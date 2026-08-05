
package com.triagain.crew.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.in.JoinCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JoinCrewService implements JoinCrewUseCase {

	private final CrewRepositoryPort crewRepositoryPort;
	private final CrewLockProperties lockProperties;
	private final TransactionTemplate txTemplate;

	/** 크루 가입 — 설정에 따라 비관적/낙관적/조건부 락 전략 분기 */
	@Override
	public JoinCrewResult joinCrew(JoinCrewCommand command) {
		if (lockProperties.isPessimistic()) {
			return txTemplate.execute(
				status -> doJoinPessimistic(command));
		}
		if (lockProperties.isConditional()) {
			return txTemplate.execute(
				status -> doJoinConditional(command));
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

	/** 조건부 원자적 UPDATE 가입 — 정원은 DB predicate, 중복은 유니크 제약(재시도 없음) */
	private JoinCrewResult doJoinConditional(JoinCrewCommand command) {
		Crew crew = crewRepositoryPort.findById(command.crewId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
		if (!crew.isPublic()) {
			throw new BusinessException(ErrorCode.CREW_NOT_PUBLIC);
		}
		CrewMember member = crew.addMemberSkipCapacityCheck(command.userId());
		if (crewRepositoryPort.incrementMembersIfNotFull(crew.getId()) == 0) {
			throw new BusinessException(ErrorCode.CREW_FULL);
		}
		try {
			// 이 경로에서 기대되는 유일한 무결성 위반은 (crew_id, user_id) 유니크 제약뿐임
			// saveMemberAndFlush로 즉시 flush → 유니크 위반이 여기서 DataIntegrityViolationException으로 변환
			// (플레인 saveMember면 커밋 시점에 터져 catch가 작동 안 함)
			crewRepositoryPort.saveMemberAndFlush(member);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
		}
		return new JoinCrewResult(
			member.getUserId(), member.getCrewId(),
			member.getRole(), crew.getCurrentMembers(),
			member.getJoinedAt());
	}
}
