
package com.triagain.crew.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.port.in.JoinCrewUseCase;
import com.triagain.crew.port.out.CrewJoinRedisPort;
import com.triagain.crew.port.out.CrewJoinRedisPort.Approval;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JoinCrewService implements JoinCrewUseCase {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final CrewRepositoryPort crewRepositoryPort;
	private final CrewLockProperties lockProperties;
	private final TransactionTemplate txTemplate;
	private final CrewJoinRedisPort crewJoinRedisPort;
	private final Clock clock;

	/** 크루 가입 — 설정의 네 전략을 명시적으로 선택. REDIS_ASYNC는 Redis 승인만 하고 DB에 쓰지 않는다 */
	@Override
	public JoinCrewResult joinCrew(JoinCrewCommand command) {
		return switch (Objects.requireNonNull(lockProperties.getLockStrategy(),
				"triagain.crew.lock-strategy must be configured")) {
			case PESSIMISTIC -> txTemplate.execute(status -> doJoinPessimistic(command));
			case OPTIMISTIC -> joinWithOptimisticRetry(command);
			case CONDITIONAL -> txTemplate.execute(status -> doJoinConditional(command));
			case REDIS_ASYNC -> joinRedisAsync(command);
		};
	}

	/**
	 * Redis 선착순 승인 — DB 조회·공개·상태/마감 검증 후 Redis가 중복·정원을 판단한다.
	 * DB 저장·TransactionTemplate 미사용, Redis 실패 시 DB 전략으로 fallback하지 않는다.
	 */
	private JoinCrewResult joinRedisAsync(JoinCrewCommand command) {
		Crew crew = crewRepositoryPort.findById(command.crewId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
		if (!crew.isPublic()) {
			throw new BusinessException(ErrorCode.CREW_NOT_PUBLIC);
		}
		crew.validateJoinable();
		// 한 번만 읽어 pending confirmedAt과 응답 joinedAt에 같은 시각을 쓴다
		OffsetDateTime confirmedAt = OffsetDateTime.ofInstant(clock.instant(), SEOUL).truncatedTo(ChronoUnit.MILLIS);
		Approval approval = crewJoinRedisPort.approve(crew.getId(), command.userId(), confirmedAt);
		return switch (approval.status()) {
			case JOIN_SUCCESS -> new JoinCrewResult(command.userId(), crew.getId(), CrewRole.MEMBER,
				approval.currentMembers(), confirmedAt.toLocalDateTime());
			case ALREADY_JOINED -> throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
			case CREW_FULL -> throw new BusinessException(ErrorCode.CREW_FULL);
			// 미초기화·상태 불일치는 사용자 잘못이 아니다 → IllegalStateException = 500/C002 (C001 금지)
			case NOT_INITIALIZED, INVALID_STATE -> throw new IllegalStateException(
				approval.status() + ": crew join Redis state rejected crew " + crew.getId());
		};
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
