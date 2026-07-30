package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.in.ChallengeQueryUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChallengeQueryService implements ChallengeQueryUseCase {

	private final ChallengeRepositoryPort challengeRepositoryPort;
	private final FindOrCreateActiveChallengeService findOrCreateActiveChallengeService;

	@Override
	public Optional<ChallengeInfoDto> findById(String challengeId) {
		return challengeRepositoryPort.findById(challengeId)
				.map(this::toDto);
	}

	@Override
	public Optional<ChallengeInfoDto> findActiveByUserIdAndCrewId(String userId, String crewId) {
		return challengeRepositoryPort.findByUserIdAndCrewIdAndStatus(userId, crewId, ChallengeStatus.IN_PROGRESS)
				.map(this::toDto);
	}

	@Override
	@Transactional
	public ChallengeInfoDto findOrCreateActive(String userId, String crewId) {
		Challenge challenge = findOrCreateActiveChallengeService.findOrCreate(userId, crewId);
		return toDto(challenge);
	}

	/** 인증 완료 기록 — completedDays 증가 + 저장, SUCCESS 시 true 반환 */
	@Override
	@Transactional
	public boolean recordCompletion(String challengeId) {
		Challenge challenge = challengeRepositoryPort.findById(challengeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
		challenge.recordCompletion();
		challengeRepositoryPort.save(challenge);
		return challenge.getStatus() == ChallengeStatus.SUCCESS;
	}

	@Override
	public int countCompletedChallenges(String userId, String crewId) {
		return challengeRepositoryPort.countSuccessByUserIdAndCrewId(userId, crewId);
	}

	/**
	 * 인증 취소 역연산 — 도메인 가드(상태 검증)로 조기 실패 후, 실제 반영은 조건부 UPDATE에 위임.
	 * D14-b(failIfUnchanged)와 대칭 패턴 — 도메인 객체는 검증만 하고 save()로 persist하지 않는다.
	 */
	@Override
	@Transactional
	public int revertCompletion(String challengeId, int expectedCompletedDays) {
		Challenge challenge = challengeRepositoryPort.findById(challengeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
		challenge.revertCompletion();
		return challengeRepositoryPort.revertCompletionIfUnchanged(challengeId, expectedCompletedDays);
	}

	private ChallengeInfoDto toDto(Challenge challenge) {
		return new ChallengeInfoDto(
				challenge.getId(),
				challenge.getUserId(),
				challenge.getCrewId(),
				challenge.getCompletedDays(),
				challenge.getTargetDays(),
				challenge.getCycleNumber(),
				challenge.getStatus().name(),
				challenge.getStartDate(),
				challenge.getDeadline(),
				challenge.getCreatedAt()
		);
	}
}
