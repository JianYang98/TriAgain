package com.triagain.verification.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.in.CancelVerificationUseCase.CancelResult;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.VerificationRepositoryPort;

@ExtendWith(MockitoExtension.class)
class CancelVerificationServiceTest {

	@Mock
	private VerificationRepositoryPort verificationRepositoryPort;

	@Mock
	private ChallengePort challengePort;

	@Mock
	private CrewPort crewPort;

	@Mock
	private VerificationPolicyProperties policyProperties;

	private static final String USER_ID = "user-1";
	private static final String CREW_ID = "crew-1";
	private static final String CHALLENGE_ID = "challenge-1";
	private static final String VERIFICATION_ID = "VRFY-1";
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDate SLOT = LocalDate.of(2026, 4, 13);

	private CancelVerificationService serviceAt(LocalDateTime fixedNow, int cutoffMinutes, int limit) {
		Clock fixedClock = Clock.fixed(fixedNow.atZone(ZONE).toInstant(), ZONE);
		// lenient — 가드 순서상 일부 테스트(G4·G3-a 등 조기 예외)는 이 값들까지 도달하지 않는다
		lenient().when(policyProperties.getCancelCutoffMinutes()).thenReturn(cutoffMinutes);
		lenient().when(policyProperties.getSlotAttemptLimit()).thenReturn(limit);
		return new CancelVerificationService(
				verificationRepositoryPort, challengePort, crewPort, policyProperties, fixedClock);
	}

	private static Verification approvedVerification(String userId, int slotAttempt) {
		return Verification.of(VERIFICATION_ID, CHALLENGE_ID, userId, CREW_ID,
				null, null, "오늘 인증", VerificationStatus.APPROVED, 0, SLOT,
				3, slotAttempt, ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
	}

	private static ChallengeInfo challengeInfo(int completedDays, String status, LocalDateTime deadline) {
		return new ChallengeInfo(CHALLENGE_ID, USER_ID, CREW_ID, completedDays, 3, status, SLOT.minusDays(2), deadline);
	}

	private static CrewVerificationWindowInfo windowInfo(LocalTime deadlineTime) {
		return new CrewVerificationWindowInfo("TEXT", "ACTIVE", SLOT.minusDays(30), SLOT.plusDays(30), false, deadlineTime);
	}

	@Test
	@DisplayName("G4 — 남의 인증을 취소하려 하면 403(CREW_ACCESS_DENIED)")
	void cancelVerification_notOwner_throwsForbidden() {
		// Given
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification("other-user", 1)));

		// When & Then
		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_ACCESS_DENIED);

		verify(verificationRepositoryPort, never()).cancelIfApproved(anyString());
	}

	@Test
	@DisplayName("G3-a — REPORTED 대상을 취소하려 하면 409 V023")
	void cancelVerification_underModeration_throwsV023() {
		// Given
		Verification reported = Verification.of(VERIFICATION_ID, CHALLENGE_ID, USER_ID, CREW_ID,
				null, null, "신고된 인증", VerificationStatus.REPORTED, 1, SLOT,
				3, 1, ReviewStatus.PENDING, LocalDateTime.now());
		given(verificationRepositoryPort.findById(VERIFICATION_ID)).willReturn(Optional.of(reported));

		// When & Then
		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_UNDER_MODERATION);
	}

	@Test
	@DisplayName("G5 — slotAttempt가 상한 이상이면 400 V021 (되돌릴 수 없는 취소 방지)")
	void cancelVerification_attemptLimitReached_throwsV021() {
		// Given — slotAttempt=3, limit=3
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification(USER_ID, 3)));

		// When & Then
		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED);

		verify(challengePort, never()).findChallengeById(anyString());
	}

	@Test
	@DisplayName("G1 — 슬롯 유효상한이 지나면 400 V019")
	void cancelVerification_afterWindow_throwsV019() {
		// Given — 마감 23:59:59, now=슬롯 다음날(마감 경과)
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification(USER_ID, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(2, "IN_PROGRESS", SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo(LocalTime.of(23, 59, 59)));

		// When & Then
		assertThatThrownBy(() -> serviceAt(SLOT.plusDays(1).atTime(0, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_WINDOW_CLOSED);
	}

	@Test
	@DisplayName("G2 경계 — 마감 5분 전(컷오프 시각)이면 이미 400 V020으로 차단된다")
	void cancelVerification_atCutoffBoundary_throwsV020() {
		// Given — 슬롯 마감 22:00, 컷오프 5분 → 취소 컷오프 시각은 21:55(그 시각부터 차단)
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification(USER_ID, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(2, "IN_PROGRESS", SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo(LocalTime.of(22, 0, 0)));

		// When & Then — 21:55:00 정각(컷오프 경계)은 이미 차단
		assertThatThrownBy(() -> serviceAt(SLOT.atTime(21, 55, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_CANCEL_TOO_LATE);
	}

	@Test
	@DisplayName("G2 경계 — 컷오프 1분 전이면 통과한다")
	void cancelVerification_justBeforeCutoff_passes() {
		// Given — 슬롯 마감 22:00, 컷오프 5분 → 21:54는 컷오프(21:55) 이전이라 통과해야 함
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification(USER_ID, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(2, "IN_PROGRESS", SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo(LocalTime.of(22, 0, 0)));
		given(verificationRepositoryPort.cancelIfApproved(VERIFICATION_ID)).willReturn(1);
		given(challengePort.revertCompletion(CHALLENGE_ID, 2)).willReturn(1);

		// When & Then — 예외 없이 성공
		CancelResult result = serviceAt(SLOT.atTime(21, 54, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID);
		assertThat(result.status()).isEqualTo(VerificationStatus.CANCELLED);
	}

	@Test
	@DisplayName("정상 취소 — cancelIfApproved 성공 시 challenge revertCompletion까지 수행되고 결과에 반영된다")
	void cancelVerification_success_revertsChallengeAndReturnsProgress() {
		// Given — 3일차 취소(SUCCESS → IN_PROGRESS(2))
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification(USER_ID, 3)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(
						Optional.of(challengeInfo(3, "SUCCESS", SLOT.plusDays(3).atTime(23, 59, 59))),
						Optional.of(challengeInfo(2, "IN_PROGRESS", SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo(LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.cancelIfApproved(VERIFICATION_ID)).willReturn(1);
		given(challengePort.revertCompletion(CHALLENGE_ID, 3)).willReturn(1);

		// When — slotAttempt=3인데도 취소는 "상한 이상이면 차단"(>=)이므로 limit을 4로 둬 통과시킨다
		CancelResult result = serviceAt(SLOT.atTime(10, 0), 5, 4)
				.cancelVerification(VERIFICATION_ID, USER_ID);

		// Then
		assertThat(result.verificationId()).isEqualTo(VERIFICATION_ID);
		assertThat(result.status()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(result.slotAttempt()).isEqualTo(3);
		assertThat(result.challengeProgress().completedDays()).isEqualTo(2);
		assertThat(result.challengeProgress().status()).isEqualTo("IN_PROGRESS");
		verify(challengePort).revertCompletion(CHALLENGE_ID, 3);
	}

	@Test
	@DisplayName("멱등 — 이미 CANCELLED(affected==0)면 challenge를 건드리지 않고 200으로 현재 값을 반환한다 (G-1)")
	void cancelVerification_alreadyCancelled_idempotentNoChallengeMutation() {
		// Given — 조건부 UPDATE 시점에 이미 다른 요청이 CANCELLED로 바꿔놓아 affected==0
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification(USER_ID, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(2, "IN_PROGRESS", SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo(LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.cancelIfApproved(VERIFICATION_ID)).willReturn(0);

		// When
		CancelResult result = serviceAt(SLOT.atTime(10, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID);

		// Then — 200 멱등, challenge 무변경(completedDays=2 그대로), revertCompletion 미호출
		assertThat(result.status()).isEqualTo(VerificationStatus.CANCELLED);
		assertThat(result.challengeProgress().completedDays()).isEqualTo(2);
		verify(challengePort, never()).revertCompletion(anyString(), anyInt());
	}

	@Test
	@DisplayName("챌린지 조건부 UPDATE가 스냅샷 불일치로 실패(affected!=1)하면 예외를 던져 트랜잭션을 롤백한다")
	void cancelVerification_challengeRevertMismatch_throws() {
		// Given — verification은 성공적으로 CANCELLED 전이되지만, challenge 쪽 CAS가 실패(경합)
		given(verificationRepositoryPort.findById(VERIFICATION_ID))
				.willReturn(Optional.of(approvedVerification(USER_ID, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(2, "IN_PROGRESS", SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo(LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.cancelIfApproved(VERIFICATION_ID)).willReturn(1);
		given(challengePort.revertCompletion(CHALLENGE_ID, 2)).willReturn(0);

		// When & Then
		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 5, 3)
				.cancelVerification(VERIFICATION_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
	}
}
