package com.triagain.verification.application;

import java.time.LocalDateTime;

import com.triagain.common.domain.DeadlinePolicy;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.VerificationStatus;

/**
 * 인증 취소·수정 공통 가드 헬퍼 — 정적 함수만 제공한다. 포트 호출은 헬퍼에 넣지 않고 호출자(Service)에 남긴다
 * (락 여부·테스트 스텁 무변경 — solo-habit의 HabitAccessGuard 판단 계승).
 * <p>
 * 🔴 G3(대상이 CANCELLED일 때의 반응)는 취소·수정이 정반대다 — 하나의 requireMutable로 합치지 않는다(G-1).
 * 취소는 CANCELLED를 여기서 막지 않는다 — 조건부 UPDATE(cancelIfApproved)의 affected==0이 200 멱등으로 처리한다.
 * 수정은 {@link #requireActiveForUpdate}로 CANCELLED를 409 V022로 명시 차단한다.
 */
final class VerificationMutationGuard {

	private VerificationMutationGuard() {
	}

	/** G4 — 소유자 검증. 남의 인증이면 403 */
	static void requireOwner(Verification verification, String userId) {
		if (!verification.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
		}
	}

	/** G3-a — moderation 차단(취소·수정 공통). REPORTED/HIDDEN/REJECTED면 409 V023 */
	static void requireNotUnderModeration(Verification verification) {
		VerificationStatus status = verification.getStatus();
		if (status == VerificationStatus.REPORTED
				|| status == VerificationStatus.HIDDEN
				|| status == VerificationStatus.REJECTED) {
			throw new BusinessException(ErrorCode.VERIFICATION_UNDER_MODERATION);
		}
	}

	/** G3-b — 수정 전용. 대상이 이미 CANCELLED(취소/치환됨)면 409 V022. 취소 경로에서는 절대 호출하지 않는다(G-1) */
	static void requireActiveForUpdate(Verification verification) {
		if (verification.getStatus() == VerificationStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.VERIFICATION_NOT_ACTIVE);
		}
	}

	/** G1 — 슬롯 유효상한(grace 미포함) 이내인지. 지났으면 400 V019 (취소·수정 공통) */
	static void requireWithinWindow(LocalDateTime now, LocalDateTime effectiveSlotDeadline) {
		if (!now.isBefore(effectiveSlotDeadline)) {
			throw new BusinessException(ErrorCode.VERIFICATION_WINDOW_CLOSED);
		}
	}

	/** G2 — 취소 전용 컷오프(슬롯 유효상한 − cutoffMinutes) 이내인지. 임박했으면 400 V020 */
	static void requireNotTooLate(LocalDateTime now, LocalDateTime effectiveSlotDeadline, int cutoffMinutes) {
		LocalDateTime cancelDeadline = DeadlinePolicy.cancelDeadline(effectiveSlotDeadline, cutoffMinutes);
		if (!now.isBefore(cancelDeadline)) {
			throw new BusinessException(ErrorCode.VERIFICATION_CANCEL_TOO_LATE);
		}
	}

	/** G5 — 슬롯당 제출 상한(취소·수정 공통). 대상 행의 slotAttempt가 상한 이상이면 400 V021 */
	static void requireAttemptAvailable(int slotAttempt, int limit) {
		if (slotAttempt >= limit) {
			throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED);
		}
	}
}
