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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.in.UpdateVerificationUseCase.UpdateCommand;
import com.triagain.verification.port.in.UpdateVerificationUseCase.UpdateResult;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;

@ExtendWith(MockitoExtension.class)
class UpdateVerificationServiceTest {

	@Mock
	private VerificationRepositoryPort verificationRepositoryPort;

	@Mock
	private UploadSessionRepositoryPort uploadSessionRepositoryPort;

	@Mock
	private ChallengePort challengePort;

	@Mock
	private CrewPort crewPort;

	@Mock
	private StoragePort storagePort;

	@Mock
	private VerificationPolicyProperties policyProperties;

	private static final String USER_ID = "user-1";
	private static final String CREW_ID = "crew-1";
	private static final String CHALLENGE_ID = "challenge-1";
	private static final String OLD_ID = "VRFY-OLD";
	private static final Long SESSION_ID = 99L;
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDate SLOT = LocalDate.of(2026, 4, 13);

	private UpdateVerificationService serviceAt(LocalDateTime fixedNow, int limit) {
		Clock fixedClock = Clock.fixed(fixedNow.atZone(ZONE).toInstant(), ZONE);
		lenient().when(policyProperties.getSlotAttemptLimit()).thenReturn(limit);
		return new UpdateVerificationService(
				verificationRepositoryPort, uploadSessionRepositoryPort, challengePort,
				crewPort, storagePort, policyProperties, fixedClock);
	}

	private static Verification oldTextVerification(String userId, VerificationStatus status, int slotAttempt) {
		return Verification.of(OLD_ID, CHALLENGE_ID, userId, CREW_ID,
				null, null, "옛 텍스트", status, 0, SLOT, 2, slotAttempt, ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
	}

	private static Verification oldPhotoVerification(Long uploadSessionId, String imageUrl, int slotAttempt) {
		return Verification.of(OLD_ID, CHALLENGE_ID, USER_ID, CREW_ID,
				uploadSessionId, imageUrl, "옛 캡션", VerificationStatus.APPROVED, 0, SLOT, 2, slotAttempt,
				ReviewStatus.NOT_REQUIRED, LocalDateTime.now());
	}

	private static ChallengeInfo challengeInfo(int completedDays, LocalDateTime deadline) {
		return new ChallengeInfo(CHALLENGE_ID, USER_ID, CREW_ID, completedDays, 3, "IN_PROGRESS", SLOT.minusDays(2), deadline);
	}

	private static CrewVerificationWindowInfo windowInfo(String type, LocalTime deadlineTime) {
		return new CrewVerificationWindowInfo(type, "ACTIVE", SLOT.minusDays(30), SLOT.plusDays(30), false, deadlineTime);
	}

	@Test
	@DisplayName("G4 — 남의 인증을 수정하려 하면 403(CREW_ACCESS_DENIED)")
	void updateVerification_notOwner_throwsForbidden() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification("other-user", VerificationStatus.APPROVED, 1)));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "새 텍스트");

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_ACCESS_DENIED);
	}

	@Test
	@DisplayName("G3-a — REPORTED 대상을 수정하려 하면 409 V023")
	void updateVerification_underModeration_throwsV023() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.REPORTED, 1)));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "새 텍스트");

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_UNDER_MODERATION);
	}

	@Test
	@DisplayName("G3-b — CANCELLED 대상을 PATCH하면 409 V022 (취소의 200 멱등과 대비, 모순1 반대편)")
	void updateVerification_alreadyCancelled_throwsV022() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.CANCELLED, 1)));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "새 텍스트");

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_NOT_ACTIVE);

		verify(verificationRepositoryPort, never()).cancelIfApproved(anyString());
	}

	@Test
	@DisplayName("G5 — slotAttempt가 상한 이상이면 400 V021")
	void updateVerification_attemptLimitReached_throwsV021() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.APPROVED, 3)));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "새 텍스트");

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED);
	}

	@Test
	@DisplayName("G1 — 슬롯 유효상한이 지나면 400 V019 (G2 미적용 — 컷오프 없이 마감 정각까지 허용)")
	void updateVerification_afterWindow_throwsV019() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.APPROVED, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "새 텍스트");

		assertThatThrownBy(() -> serviceAt(SLOT.plusDays(1).atTime(0, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_WINDOW_CLOSED);
	}

	@Test
	@DisplayName("P3 — TEXT 크루에 uploadSessionId를 담아 PATCH하면 400 V017")
	void updateVerification_textCrewWithSession_throwsV017() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.APPROVED, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, SESSION_ID, null);

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_REQUIRED);

		verify(verificationRepositoryPort, never()).cancelIfApproved(anyString());
	}

	@Test
	@DisplayName("바꿀 내용 없음 — uploadSessionId·textContent 둘 다 없으면 400 INVALID_INPUT")
	void updateVerification_bothNull_throwsInvalidInput() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.APPROVED, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, null);

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("P1/G-10/G-11 — PHOTO 크루에서 세션 없이 텍스트만 수정하면 image_url 승계 + 새 행 upload_session_id는 NULL")
	void updateVerification_photoCrewTextOnly_carriesImageUrlWithNullSession() {
		// Given
		Verification old = oldPhotoVerification(555L, "https://cdn.example.com/old.jpg", 1);
		given(verificationRepositoryPort.findById(OLD_ID)).willReturn(Optional.of(old));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.findMaxSlotAttempt(USER_ID, CREW_ID, SLOT)).willReturn(1);
		given(verificationRepositoryPort.cancelIfApproved(OLD_ID)).willReturn(1);
		given(verificationRepositoryPort.saveAndFlush(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "텍스트만 수정");

		// When
		UpdateResult result = serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command);

		// Then
		ArgumentCaptor<Verification> captor = ArgumentCaptor.forClass(Verification.class);
		verify(verificationRepositoryPort).saveAndFlush(captor.capture());
		Verification saved = captor.getValue();

		assertThat(saved.getImageUrl()).isEqualTo("https://cdn.example.com/old.jpg");
		assertThat(saved.getUploadSessionId()).isNull();   // G-10
		assertThat(saved.getTextContent()).isEqualTo("텍스트만 수정");
		assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.NOT_REQUIRED);   // G-9(팩토리 재사용 보장)
		assertThat(saved.getReportCount()).isZero();                                // G-9
		assertThat(saved.getSlotAttempt()).isEqualTo(2);
		assertThat(saved.getAttemptNumber()).isEqualTo(old.getAttemptNumber());     // attemptNumber는 옛 값 그대로

		assertThat(result.previousVerificationId()).isEqualTo(OLD_ID);
		verify(uploadSessionRepositoryPort, never()).findByIdAndUserId(any(), anyString());
	}

	@Test
	@DisplayName("G-12 — 수정은 challenge를 절대 건드리지 않는다 (revertCompletion·recordCompletion 미호출)")
	void updateVerification_neverTouchesChallenge() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.APPROVED, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.findMaxSlotAttempt(USER_ID, CREW_ID, SLOT)).willReturn(1);
		given(verificationRepositoryPort.cancelIfApproved(OLD_ID)).willReturn(1);
		given(verificationRepositoryPort.saveAndFlush(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "새 텍스트");

		serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command);

		verify(challengePort, never()).revertCompletion(anyString(), anyInt());
		verify(challengePort, never()).recordCompletion(anyString());
	}

	@Test
	@DisplayName("새 사진 업로드 세션 검증 — 존재하지 않으면 V004")
	void updateVerification_newSessionNotFound_throwsV004() {
		Verification old = oldPhotoVerification(555L, "https://cdn.example.com/old.jpg", 1);
		given(verificationRepositoryPort.findById(OLD_ID)).willReturn(Optional.of(old));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.findMaxSlotAttempt(USER_ID, CREW_ID, SLOT)).willReturn(1);
		given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID, USER_ID)).willReturn(Optional.empty());

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, SESSION_ID, null);

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_FOUND);

		verify(verificationRepositoryPort, never()).cancelIfApproved(anyString());
	}

	@Test
	@DisplayName("새 사진으로 교체 성공 — 새 세션 검증 후 imageUrl 갱신, 새 행이 새 세션ID를 보유한다"
			+ "(G-10 정정, 사진교체는 텍스트수정과 달리 NULL이 아니라 세션ID 보유)")
	void updateVerification_newPhoto_success() {
		Verification old = oldPhotoVerification(555L, "https://cdn.example.com/old.jpg", 1);
		UploadSession newSession = UploadSession.of(SESSION_ID, USER_ID, CREW_ID, "images/new.jpg", "image/jpeg",
				UploadSessionStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now());

		given(verificationRepositoryPort.findById(OLD_ID)).willReturn(Optional.of(old));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.findMaxSlotAttempt(USER_ID, CREW_ID, SLOT)).willReturn(1);
		given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID, USER_ID)).willReturn(Optional.of(newSession));
		given(storagePort.getImageUrl("images/new.jpg")).willReturn("https://cdn.example.com/new.jpg");
		given(verificationRepositoryPort.cancelIfApproved(OLD_ID)).willReturn(1);
		given(verificationRepositoryPort.saveAndFlush(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, SESSION_ID, "새 캡션");

		UpdateResult result = serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command);

		assertThat(result.imageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
		ArgumentCaptor<Verification> captor = ArgumentCaptor.forClass(Verification.class);
		verify(verificationRepositoryPort).saveAndFlush(captor.capture());
		// 결함1 회귀: 새 세션ID를 저장해야 uk_verifications_upload_session이 그 세션의 재사용을 막는다
		assertThat(captor.getValue().getUploadSessionId()).isEqualTo(SESSION_ID);
	}

	@Test
	@DisplayName("옛 행 조건부 UPDATE가 경합으로 실패(affected!=1)하면 409 V022로 예외를 던진다")
	void updateVerification_cancelIfApprovedMismatch_throwsV022() {
		given(verificationRepositoryPort.findById(OLD_ID))
				.willReturn(Optional.of(oldTextVerification(USER_ID, VerificationStatus.APPROVED, 1)));
		given(challengePort.findChallengeById(CHALLENGE_ID))
				.willReturn(Optional.of(challengeInfo(1, SLOT.plusDays(3).atTime(23, 59, 59))));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.findMaxSlotAttempt(USER_ID, CREW_ID, SLOT)).willReturn(1);
		given(verificationRepositoryPort.cancelIfApproved(OLD_ID)).willReturn(0);

		UpdateCommand command = new UpdateCommand(OLD_ID, USER_ID, null, "새 텍스트");

		assertThatThrownBy(() -> serviceAt(SLOT.atTime(10, 0), 3).updateVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_NOT_ACTIVE);

		verify(verificationRepositoryPort, never()).saveAndFlush(any());
	}
}
