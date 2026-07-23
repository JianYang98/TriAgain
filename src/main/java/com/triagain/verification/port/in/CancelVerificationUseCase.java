package com.triagain.verification.port.in;

import com.triagain.verification.domain.vo.VerificationStatus;

public interface CancelVerificationUseCase {

	/** 인증 취소 — 마감 전 유저가 스스로 취소. 이미 CANCELLED인 대상에 재요청하면 멱등하게 현재 상태를 반환한다 */
	CancelResult cancelVerification(String verificationId, String userId);

	record CancelResult(String verificationId, VerificationStatus status, int slotAttempt,
			ChallengeProgress challengeProgress) {
	}

	record ChallengeProgress(String challengeId, int completedDays, int targetDays, String status) {
	}
}
