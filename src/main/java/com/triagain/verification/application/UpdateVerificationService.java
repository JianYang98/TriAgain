package com.triagain.verification.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.domain.DeadlinePolicy;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.in.UpdateVerificationUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;

import lombok.RequiredArgsConstructor;

/**
 * 인증 수정 — in-place UPDATE가 아니라 치환(replacement)이다. 옛 행을 CANCELLED로 무효화하고
 * 새 행을 INSERT한다. challenge는 절대 건드리지 않는다(G-12, step1 §1-1).
 */
@Service
@RequiredArgsConstructor
public class UpdateVerificationService implements UpdateVerificationUseCase {

	private final VerificationRepositoryPort verificationRepositoryPort;
	private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
	private final ChallengePort challengePort;
	private final CrewPort crewPort;
	private final StoragePort storagePort;
	private final VerificationPolicyProperties policyProperties;
	private final Clock clock;

	@Override
	@Transactional(timeout = 10)
	public UpdateResult updateVerification(UpdateCommand command) {
		LocalDateTime now = LocalDateTime.now(clock);   // 진입 시 1회 스냅샷(G-14)

		Verification oldVerification = verificationRepositoryPort.findById(command.verificationId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

		// 가드 순서 고정: G4(소유) → G3-a(moderation, V023) → G3-b(CANCELLED, V022) → G5(상한) → G1(마감창).
		// G2(컷오프)는 수정에 미적용(step1 §1-3).
		VerificationMutationGuard.requireOwner(oldVerification, command.userId());
		VerificationMutationGuard.requireNotUnderModeration(oldVerification);
		VerificationMutationGuard.requireActiveForUpdate(oldVerification);
		VerificationMutationGuard.requireAttemptAvailable(
				oldVerification.getSlotAttempt(), policyProperties.getSlotAttemptLimit());

		ChallengeInfo challenge = challengePort.findChallengeById(oldVerification.getChallengeId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
		CrewVerificationWindowInfo windowInfo = crewPort.getCrewVerificationWindowInfo(oldVerification.getCrewId());
		LocalDateTime effectiveSlotDeadline = DeadlinePolicy.effectiveSlotDeadline(
				oldVerification.getTargetDate(), windowInfo.deadlineTime(), challenge.deadline());

		VerificationMutationGuard.requireWithinWindow(now, effectiveSlotDeadline);

		validateInput(command, windowInfo.verificationType());

		int nextSlotAttempt = verificationRepositoryPort.findMaxSlotAttempt(
				oldVerification.getUserId(), oldVerification.getCrewId(), oldVerification.getTargetDate()) + 1;

		Verification newVerification = buildReplacement(
				command, oldVerification, windowInfo.verificationType(), nextSlotAttempt);

		// ① 옛 행 조건부 UPDATE — 먼저 flush되어야 새 행 INSERT와 partial unique 충돌이 없다(G-3 순서)
		int affected = verificationRepositoryPort.cancelIfApproved(oldVerification.getId());
		if (affected != 1) {
			throw new BusinessException(ErrorCode.VERIFICATION_NOT_ACTIVE);
		}

		// ② 새 행 INSERT — challenge는 무변경(recordCompletion·CrewFirstVerificationEvent 호출 없음, G-12)
		Verification saved = verificationRepositoryPort.saveAndFlush(newVerification);

		return toResult(saved, oldVerification.getId());
	}

	/** 타입별 입력 검증(step2 §3-1) — TEXT 크루의 사진 첨부, 바꿀 내용 없는 요청을 차단 */
	private void validateInput(UpdateVerificationUseCase.UpdateCommand command, String verificationType) {
		if ("TEXT".equals(verificationType) && command.uploadSessionId() != null) {
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_REQUIRED);
		}
		if (command.uploadSessionId() == null && command.textContent() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	/**
	 * 새 행 값 산출 — {@link Verification#createText}/{@link Verification#createPhoto} 팩토리를 재사용한다(G-9).
	 * {@code of()} 직접 호출 금지 — reviewStatus=NOT_REQUIRED·reportCount=0 자동 보장이 깨진다.
	 */
	private Verification buildReplacement(
			UpdateVerificationUseCase.UpdateCommand command, Verification old,
			String verificationType, int slotAttempt) {
		String textContent = command.textContent() != null ? command.textContent() : old.getTextContent();

		if (command.uploadSessionId() != null) {
			// 새 사진으로 교체 — 새 행이 그 새 세션ID를 보유해야 세션 재사용 방지가 유지된다(G-10 정정,
			// 2026-07-24 Codex). 텍스트만 수정하는 두 분기(NULL 유지)와 달리 여기는 새 세션을 발급받은
			// 경우라 NULL로 두면 uk_verifications_upload_session이 무력화된다.
			String imageUrl = resolveNewImageUrl(command, old);
			return Verification.createPhoto(
					old.getChallengeId(), old.getUserId(), old.getCrewId(),
					command.uploadSessionId(), imageUrl, textContent,
					old.getTargetDate(), old.getAttemptNumber(), slotAttempt);
		}

		if ("PHOTO".equals(verificationType)) {
			// PHOTO 크루 + uploadSessionId 없음 = 텍스트만 교체(G-11, PHOTO_REQUIRED 아님) — 기존 image_url 승계
			return Verification.createPhoto(
					old.getChallengeId(), old.getUserId(), old.getCrewId(),
					null, old.getImageUrl(), textContent,
					old.getTargetDate(), old.getAttemptNumber(), slotAttempt);
		}

		return Verification.createText(
				old.getChallengeId(), old.getUserId(), old.getCrewId(),
				textContent, old.getTargetDate(), old.getAttemptNumber(), slotAttempt);
	}

	/** 새 업로드 세션 검증 + imageUrl 조회 — CreateVerificationService의 사진 검증 로직과 동일(V004/V005/V006/V016) */
	private String resolveNewImageUrl(UpdateVerificationUseCase.UpdateCommand command, Verification old) {
		UploadSession session = uploadSessionRepositoryPort
				.findByIdAndUserId(command.uploadSessionId(), command.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

		if (session.getCrewId() != null && !session.getCrewId().equals(old.getCrewId())) {
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);
		}

		if (!session.isCompleted()) {
			if (session.isPending()) {
				throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_COMPLETED);
			}
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_EXPIRED);
		}

		return storagePort.getImageUrl(session.getImageKey());
	}

	private UpdateResult toResult(Verification saved, String previousVerificationId) {
		return new UpdateResult(
				saved.getId(), previousVerificationId, saved.getChallengeId(),
				saved.getUserId(), saved.getCrewId(), saved.getImageUrl(), saved.getTextContent(),
				saved.getStatus(), saved.getReviewStatus(), saved.getReportCount(),
				saved.getTargetDate(), saved.getSlotAttempt(), saved.getCreatedAt());
	}
}
