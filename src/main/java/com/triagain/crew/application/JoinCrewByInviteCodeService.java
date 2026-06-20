package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.port.in.JoinCrewByInviteCodeUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JoinCrewByInviteCodeService
		implements JoinCrewByInviteCodeUseCase {


	private final CrewRepositoryPort crewRepositoryPort;
	private final CrewLockProperties lockProperties;
	private final TransactionTemplate txTemplate;

	/** 초대코드로 크루 참여 — 공유 링크를 통해 참여할 때 사용 */
	@Override
	public JoinByInviteCodeResult joinByInviteCode(
			JoinByInviteCodeCommand command) {
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

	private JoinByInviteCodeResult joinWithOptimisticRetry(
			JoinByInviteCodeCommand command) {
		for (int attempt = 1; attempt <= lockProperties.getMaxRetry(); attempt++) {
			JoinByInviteCodeResult result = txTemplate.execute(
				status -> doJoinOptimistic(command));
			if (result != null) {
				return result;
			}
		}
		throw new BusinessException(ErrorCode.CREW_JOIN_CONFLICT);
	}

	private JoinByInviteCodeResult doJoinPessimistic(
			JoinByInviteCodeCommand command) {
		Crew crew = crewRepositoryPort
			.findByInviteCode(command.inviteCode())
			.orElseThrow(() -> new BusinessException(
				ErrorCode.INVALID_INVITE_CODE));
		Crew lockedCrew = crewRepositoryPort
			.findByIdWithLock(crew.getId())
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CREW_NOT_FOUND));
		return doJoin(lockedCrew, command.userId());
	}

	private JoinByInviteCodeResult doJoinOptimistic(
			JoinByInviteCodeCommand command) {
		Crew crew = crewRepositoryPort
			.findByInviteCode(command.inviteCode())
			.orElseThrow(() -> new BusinessException(
				ErrorCode.INVALID_INVITE_CODE));

		CrewMember member = crew.addMember(command.userId());
		int updated = crewRepositoryPort
			.updateCurrentMembersWithVersion(
				crew.getId(), crew.getCurrentMembers(),
				crew.getVersion());
		if (updated == 0) {
			return null;
		}
		crewRepositoryPort.saveMember(member);

		return buildResult(crew, member);
	}

	private JoinByInviteCodeResult doJoin(Crew crew, String userId) {
		CrewMember member = crew.addMember(userId);
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(member);
		return buildResult(crew, member);
	}

	/** 조건부 원자적 UPDATE 가입(초대코드 경로) — 정원=DB predicate, 중복=유니크 제약, isPublic 검증 없음(초대코드는 비공개 우회 의도) */
	private JoinByInviteCodeResult doJoinConditional(
			JoinByInviteCodeCommand command) {
		Crew crew = crewRepositoryPort.findByInviteCode(command.inviteCode())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));
		CrewMember member = crew.addMemberSkipCapacityCheck(command.userId());
		if (crewRepositoryPort.incrementMembersIfNotFull(crew.getId()) == 0) {
			throw new BusinessException(ErrorCode.CREW_FULL);
		}
		try {
			// 이 경로에서 기대되는 유일한 무결성 위반은 (crew_id, user_id) 유니크 제약뿐임
			crewRepositoryPort.saveMemberAndFlush(member);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
		}
		return buildResult(crew, member);
	}

	private JoinByInviteCodeResult buildResult(
			Crew crew, CrewMember member) {
		return new JoinByInviteCodeResult(
			member.getUserId(), member.getCrewId(),
			member.getRole(), crew.getCurrentMembers(),
			member.getJoinedAt());
	}
}
