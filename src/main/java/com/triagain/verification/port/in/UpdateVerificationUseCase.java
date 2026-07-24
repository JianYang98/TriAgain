package com.triagain.verification.port.in;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;

public interface UpdateVerificationUseCase {

	/** 인증 수정(치환) — 마감 전 텍스트/사진을 바꾼다. 내부적으로 옛 행을 CANCELLED 처리하고 새 행을 만든다 */
	UpdateResult updateVerification(UpdateCommand command);

	record UpdateCommand(String verificationId, String userId, Long uploadSessionId, String textContent) {
	}

	record UpdateResult(String verificationId, String previousVerificationId, String challengeId,
			String userId, String crewId, String imageUrl, String textContent,
			VerificationStatus status, ReviewStatus reviewStatus, int reportCount,
			LocalDate targetDate, int slotAttempt, LocalDateTime createdAt) {
	}
}
