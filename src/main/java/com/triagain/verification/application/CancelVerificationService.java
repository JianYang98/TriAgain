package com.triagain.verification.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.domain.DeadlinePolicy;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.in.CancelVerificationUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.VerificationRepositoryPort;

import lombok.RequiredArgsConstructor;

/**
 * 인증 취소 — 대상 인증을 CANCELLED로 무효화하고 챌린지 진행도를 역연산(completedDays--)한다.
 * 이미 CANCELLED인 대상에 재요청하면 200 멱등으로 처리한다(G-1, step1 §1-5·E5).
 */
@Service
@RequiredArgsConstructor
public class CancelVerificationService implements CancelVerificationUseCase {

	private final VerificationRepositoryPort verificationRepositoryPort;
	private final ChallengePort challengePort;
	private final CrewPort crewPort;
	private final VerificationPolicyProperties policyProperties;
	private final Clock clock;

	@Override
	@Transactional(timeout = 10)
	public CancelResult cancelVerification(String verificationId, String userId) {
		LocalDateTime now = LocalDateTime.now(clock);   // 진입 시 1회 스냅샷(G-14) — 모든 가드가 이 값으로 판정

		Verification verification = verificationRepositoryPort.findById(verificationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

		// 가드 순서 고정: G4(소유) → G3-a(moderation만, V023) → G5(상한) → G1(마감창) → G2(컷오프).
		// 🔴 CANCELLED는 여기서 막지 않는다 — ⑤의 조건부 UPDATE(affected==0)가 멱등 200으로 흘려보낸다(G-1).
		VerificationMutationGuard.requireOwner(verification, userId);
		VerificationMutationGuard.requireNotUnderModeration(verification);
		VerificationMutationGuard.requireAttemptAvailable(
				verification.getSlotAttempt(), policyProperties.getSlotAttemptLimit());

		ChallengeInfo challenge = challengePort.findChallengeById(verification.getChallengeId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
		CrewVerificationWindowInfo windowInfo = crewPort.getCrewVerificationWindowInfo(verification.getCrewId());
		LocalDateTime effectiveSlotDeadline = DeadlinePolicy.effectiveSlotDeadline(
				verification.getTargetDate(), windowInfo.deadlineTime(), challenge.deadline());

		VerificationMutationGuard.requireWithinWindow(now, effectiveSlotDeadline);
		VerificationMutationGuard.requireNotTooLate(
				now, effectiveSlotDeadline, policyProperties.getCancelCutoffMinutes());

		int snapshotCompletedDays = challenge.completedDays();   // 조건부 UPDATE 순서 고정(G-3) 전에 캡처

		// ① verifications 조건부 UPDATE — affected==0이면 이미 CANCELLED(순차 재요청) → challenge 무변경 멱등 반환
		int verificationAffected = verificationRepositoryPort.cancelIfApproved(verificationId);
		if (verificationAffected == 0) {
			return toResult(verification.getId(), verification.getSlotAttempt(), challenge);
		}

		// ② challenges 조건부 UPDATE — ①이 성공한 뒤에만 수행(데드락 창 회피, G-3)
		int challengeAffected = challengePort.revertCompletion(challenge.id(), snapshotCompletedDays);
		if (challengeAffected != 1) {
			throw new BusinessException(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);   // 트랜잭션 전체 롤백
		}

		ChallengeInfo updatedChallenge = challengePort.findChallengeById(challenge.id())
				.orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

		return toResult(verification.getId(), verification.getSlotAttempt(), updatedChallenge);
	}

	private CancelResult toResult(String verificationId, int slotAttempt, ChallengeInfo challenge) {
		ChallengeProgress progress = new ChallengeProgress(
				challenge.id(), challenge.completedDays(), challenge.targetDays(), challenge.status());
		return new CancelResult(verificationId, VerificationStatus.CANCELLED, slotAttempt, progress);
	}
}
