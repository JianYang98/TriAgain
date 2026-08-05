package com.triagain.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.verification.application.event.ChallengeSuccessEvent;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.in.CreateVerificationUseCase.CreateVerificationCommand;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;

@ExtendWith(MockitoExtension.class)
class CreateVerificationServiceTest {

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
	private ApplicationEventPublisher eventPublisher;

	// [신규 스캐폴딩] 실제 시스템 Clock을 감싸는 spy — 기존 9개 테스트는 LocalDate.now() 기반 동적 값을 그대로 쓰므로
	// 무변경으로 계속 통과한다. Clock.fixed가 필요한 신규(T-U*) 테스트는 serviceAt()으로 별도 인스턴스를 만든다.
	@Spy
	private Clock clock = Clock.systemDefaultZone();

	@Mock
	private VerificationPolicyProperties policyProperties;

	@InjectMocks
	private CreateVerificationService createVerificationService;

	@BeforeEach
	void setUpPolicy() {
		// 슬롯 상한(G5, V021) 기본값 — 대부분의 기존 테스트는 상한과 무관하므로 lenient로 등록한다
		lenient().when(policyProperties.getSlotAttemptLimit()).thenReturn(3);
	}

	private static final String USER_ID = "user-1";
	private static final String CREW_ID = "crew-1";
	private static final String CHALLENGE_ID = "challenge-1";
	private static final Long SESSION_ID = 1L;
	private static final ZoneId ZONE = ZoneId.systemDefault();
	// T-U* 테스트 전용 고정 기준일 — 슬롯 귀속 규칙 검증에 사용 (기존 9개 테스트의 LocalDate.now() 값과는 무관)
	private static final LocalDate D = LocalDate.of(2026, 4, 13);

	private static ChallengeInfo challengeInfo() {
		return new ChallengeInfo(
				CHALLENGE_ID, USER_ID, CREW_ID,
				2, 3, "IN_PROGRESS",
				LocalDate.now().minusDays(2),
				LocalDateTime.now().plusHours(1)
		);
	}

	private static UploadSession completedSession() {
		return UploadSession.of(SESSION_ID, USER_ID, CREW_ID, "images/test.jpg", "image/jpeg",
				UploadSessionStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now());
	}

	/** T-U* 전용 챌린지 팩토리 — 고정 startDate/completedDays로 슬롯을 결정론적으로 구성 */
	private static ChallengeInfo challengeInfo(int completedDays, LocalDate startDate, LocalDateTime deadline) {
		return new ChallengeInfo(CHALLENGE_ID, USER_ID, CREW_ID, completedDays, 3, "IN_PROGRESS", startDate, deadline);
	}

	/** T-U* 전용 크루 인증 윈도우 스텁 — verificationType·deadlineTime만 실제로 쓰인다 */
	private static CrewVerificationWindowInfo windowInfo(String verificationType, LocalTime deadlineTime) {
		return new CrewVerificationWindowInfo(
				verificationType, "ACTIVE",
				D.minusDays(30), D.plusDays(30), false, deadlineTime);
	}

	/** T-U* 전용 — 고정 Clock을 주입한 별도 서비스 인스턴스 (테스트마다 다른 fixedNow가 필요하므로 @InjectMocks 미사용) */
	private CreateVerificationService serviceAt(LocalDateTime fixedNow) {
		Clock fixedClock = Clock.fixed(fixedNow.atZone(ZONE).toInstant(), ZONE);
		return new CreateVerificationService(
				verificationRepositoryPort, uploadSessionRepositoryPort, challengePort,
				crewPort, storagePort, eventPublisher, fixedClock, policyProperties);
	}

	@Test
	@DisplayName("사진 인증 성공 시 세션은 COMPLETED를 유지한다 — 중복 방지는 DB UNIQUE constraint")
	void createPhotoVerification_success_sessionStaysCompleted() {
		// Given
		ChallengeInfo challenge = challengeInfo();
		UploadSession session = completedSession();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, SESSION_ID, "오늘도 완료!");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(23, 59,
				59)));
		given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID, USER_ID))
				.willReturn(Optional.of(session));
		given(storagePort.getImageUrl("images/test.jpg")).willReturn("https://cdn.example.com/images/test.jpg");
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When
		createVerificationService.createVerification(command);

		// Then
		assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
		verify(uploadSessionRepositoryPort, never()).save(session);
	}

	@Test
	@DisplayName("crewId만으로 인증 시 findOrCreateActiveChallenge 호출")
	void createVerification_crewIdOnly_usesAutoCreate() {
		// Given
		ChallengeInfo challenge = challengeInfo();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When
		createVerificationService.createVerification(command);

		// Then
		verify(challengePort).findOrCreateActiveChallenge(USER_ID, CREW_ID);
		verify(challengePort).recordCompletion(CHALLENGE_ID);
	}

	@Test
	@DisplayName("challengeId + crewId 교차 검증 — crewId 불일치 시 CHALLENGE_CREW_MISMATCH 예외")
	void createVerification_challengeCrewMismatch_throws() {
		// Given
		ChallengeInfo challenge = challengeInfo(); // crewId = "crew-1"
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, "crew-999", null, "텍스트");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));

		// When & Then
		assertThatThrownBy(() -> createVerificationService.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CHALLENGE_CREW_MISMATCH);
	}

	@Test
	@DisplayName("FAILED 상태 챌린지로 인증 시 CHALLENGE_NOT_IN_PROGRESS 예외")
	void createVerification_failedChallenge_throws() {
		// Given
		ChallengeInfo failedChallenge = new ChallengeInfo(
				CHALLENGE_ID, USER_ID, CREW_ID,
				1, 3, "FAILED",
				LocalDate.now().minusDays(1),
				LocalDateTime.now().plusHours(1)
		);
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(failedChallenge));

		// When & Then
		assertThatThrownBy(() -> createVerificationService.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
	}

	@Test
	@DisplayName("TEXT 인증도 grace period 5분 적용 — deadline + 3분 → 성공")
	void createTextVerification_withinGracePeriod_success() {
		// Given — deadline이 3분 전 (grace period 5분 이내)
		ChallengeInfo challenge = new ChallengeInfo(
				CHALLENGE_ID, USER_ID, CREW_ID,
				2, 3, "IN_PROGRESS",
				LocalDate.now().minusDays(2),
				LocalDateTime.now().minusMinutes(3)
		);
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When
		createVerificationService.createVerification(command);

		// Then — 예외 없이 성공
		verify(verificationRepositoryPort).save(any(Verification.class));
	}

	@Test
	@DisplayName("TEXT 인증 grace period 초과 — deadline + 6분 → VERIFICATION_DEADLINE_EXCEEDED")
	void createTextVerification_exceedsGracePeriod_throws() {
		// Given — deadline이 6분 전 (grace period 5분 초과)
		ChallengeInfo challenge = new ChallengeInfo(
				CHALLENGE_ID, USER_ID, CREW_ID,
				2, 3, "IN_PROGRESS",
				LocalDate.now().minusDays(2),
				LocalDateTime.now().minusMinutes(6)
		);
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);

		// When & Then
		assertThatThrownBy(() -> createVerificationService.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("crewId만으로 인증 시 비회원이면 CREW_ACCESS_DENIED + findOrCreateActiveChallenge 미호출")
	void createVerification_crewIdOnly_nonMember_blocksBeforeChallenge() {
		// Given
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		willThrow(new BusinessException(ErrorCode.CREW_ACCESS_DENIED))
				.given(crewPort).validateMembership(CREW_ID, USER_ID);

		// When & Then
		assertThatThrownBy(() -> createVerificationService.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_ACCESS_DENIED);

		verify(challengePort, never()).findOrCreateActiveChallenge(any(), any());
	}

	@Test
	@DisplayName("challengeId만으로 사진 인증 시 session의 crewId와 challenge의 crewId가 다르면 UPLOAD_SESSION_CREW_MISMATCH 예외")
	void createPhotoVerification_challengeIdOnly_crossCrewSession_throws() {
		// Given — session은 crew-999, challenge는 crew-1
		UploadSession crossCrewSession = UploadSession.of(
				SESSION_ID, USER_ID, "crew-999", "images/test.jpg", "image/jpeg",
				UploadSessionStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now());
		ChallengeInfo challenge = challengeInfo(); // crewId = CREW_ID ("crew-1")
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, SESSION_ID, "사진 인증"); // crewId = null

		given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID, USER_ID))
				.willReturn(Optional.of(crossCrewSession));
		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(23, 59,
				59)));

		// When & Then
		assertThatThrownBy(() -> createVerificationService.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);

		verify(verificationRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("recordCompletion이 true이면 챌린지 성공 알림이 발송된다")
	void createVerification_challengeSuccess_sendsNotification() {
		// Given
		ChallengeInfo challenge = challengeInfo();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(challengePort.recordCompletion(CHALLENGE_ID)).willReturn(true);

		// When
		createVerificationService.createVerification(command);

		// Then — 챌린지 성공 이벤트 발행 확인 (트랜잭션 커밋 후 listener가 알림 발송)
		verify(eventPublisher).publishEvent(new ChallengeSuccessEvent(USER_ID, CREW_ID));
	}

	@Test
	@DisplayName("recordCompletion이 false이면 챌린지 성공 알림이 발송되지 않는다")
	void createVerification_challengeNotComplete_noNotification() {
		// Given
		ChallengeInfo challenge = challengeInfo();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(challengePort.recordCompletion(CHALLENGE_ID)).willReturn(false);

		// When
		createVerificationService.createVerification(command);

		// Then — 챌린지 성공 이벤트 미발행 확인
		verify(eventPublisher, never()).publishEvent(any(ChallengeSuccessEvent.class));
	}

	@Test
	@DisplayName("challengeId+crewId로 인증 시 비회원이면 CREW_ACCESS_DENIED + findChallengeById 미호출")
	void createVerification_challengeIdAndCrewId_nonMember_blocksBeforeChallenge() {
		// Given
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, CREW_ID, null, "텍스트 인증");

		willThrow(new BusinessException(ErrorCode.CREW_ACCESS_DENIED))
				.given(crewPort).validateMembership(CREW_ID, USER_ID);

		// When & Then
		assertThatThrownBy(() -> createVerificationService.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CREW_ACCESS_DENIED);

		verify(challengePort, never()).findChallengeById(any());
	}

	// ===== T-U1~T-U9: 슬롯 귀속 + 슬롯 일일마감 통합 (step4 §5, D-A 안1·D-B 통합) =====

	@Test
	@DisplayName("T-U1 마감 23:59:59, 슬롯=D 미인증, clock=D+1 00:02 텍스트 제출 — targetDate=D로 저장된다(버그 수정 본체)")
	void slotD_midnightGraceSubmit_targetDateIsSlotD() {
		// Given — 슬롯(D)이 아직 미인증인 상태에서 자정 직후(00:02)에 텍스트 인증 제출
		ChallengeInfo challenge = challengeInfo(0, D, D.plusDays(3).atTime(23, 59, 59));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, D))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When
		serviceAt(D.plusDays(1).atTime(0, 2)).createVerification(command);

		// Then — targetDate가 인증 생성 시각(D+1)이 아니라 슬롯(D)으로 저장된다
		ArgumentCaptor<Verification> captor = ArgumentCaptor.forClass(Verification.class);
		verify(verificationRepositoryPort).save(captor.capture());
		assertThat(captor.getValue().getTargetDate()).isEqualTo(D);
	}

	@Test
	@DisplayName("T-U2 사진 requestedAt=D 23:58, 업로드 완료 후 생성 시각=D+1 00:01 — targetDate=D로 저장된다")
	void slotD_photoRequestedBeforeMidnight_targetDateIsSlotD() {
		// Given — 23:58에 업로드 요청(requestedAt), 실제 인증 생성은 자정 넘겨 00:01
		ChallengeInfo challenge = challengeInfo(0, D, D.plusDays(3).atTime(23, 59, 59));
		UploadSession session = UploadSession.of(SESSION_ID, USER_ID, CREW_ID, "images/test.jpg", "image/jpeg",
				UploadSessionStatus.COMPLETED, D.atTime(23, 58), D.atTime(23, 58));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, SESSION_ID, "사진 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID, USER_ID)).willReturn(Optional.of(session));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(23, 59,
				59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, D))
				.willReturn(false);
		given(storagePort.getImageUrl("images/test.jpg")).willReturn("https://cdn.example.com/images/test.jpg");
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When — 앵커는 requestedAt(D 23:58)이므로 clock 값(D+1 00:01)과 무관하게 슬롯=D로 귀속되어야 한다
		serviceAt(D.plusDays(1).atTime(0, 1)).createVerification(command);

		// Then
		ArgumentCaptor<Verification> captor = ArgumentCaptor.forClass(Verification.class);
		verify(verificationRepositoryPort).save(captor.capture());
		assertThat(captor.getValue().getTargetDate()).isEqualTo(D);
	}

	@Test
	@DisplayName("T-U3 D 인증 완료 후(슬롯=D+1) clock=D+1 00:02에 재제출 — targetDate=D+1로 성공한다(V003 아님, 회귀 방지)")
	void slotDPlus1_afterDVerified_midnightResubmit_success() {
		// Given — 이미 D를 인증 완료(completedDays=1)해 슬롯이 D+1로 넘어간 상태
		ChallengeInfo challenge = challengeInfo(1, D, D.plusDays(3).atTime(23, 59, 59));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, D.plusDays(1)))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When
		serviceAt(D.plusDays(1).atTime(0, 2)).createVerification(command);

		// Then
		ArgumentCaptor<Verification> captor = ArgumentCaptor.forClass(Verification.class);
		verify(verificationRepositoryPort).save(captor.capture());
		assertThat(captor.getValue().getTargetDate()).isEqualTo(D.plusDays(1));
	}

	@Test
	@DisplayName("T-U4 D 인증 완료 후(슬롯=D+1) clock=D 20:00에 재제출 — V003(하한, 몰아치기 봉쇄) + 기존 중복체크와 동일한 페이로드 형태")
	void slotDPlus1_afterDVerified_sameDayResubmit_throwsV003() {
		// Given — 슬롯은 D+1인데 앵커(now)는 아직 D — 하루치를 몰아 채우는 것을 방지
		ChallengeInfo challenge = challengeInfo(1, D, D.plusDays(3).atTime(23, 59, 59));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));

		// When & Then
		assertThatThrownBy(() -> serviceAt(D.atTime(20, 0)).createVerification(command))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> {
					BusinessException be = (BusinessException) e;
					assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_ALREADY_EXISTS);
					// 기존 중복체크(:71) V003도 인자 없이 던지므로 페이로드 형태가 동일함을 함께 고정한다 (step4 §1-6)
					assertThat(be.getArgs()).isNull();
				});

		verify(verificationRepositoryPort, never()).existsActiveByUserIdAndCrewIdAndTargetDate(any(), any(), any());
	}

	@Test
	@DisplayName("T-U5 deadlineTime=21:00, 2일차 슬롯=D, clock=D 23:00 제출 — 슬롯 일일마감 초과로 V002 (07-18 건 해소)")
	void slotDailyDeadlineExceeded_throwsV002() {
		// Given — 사이클 마감(challenge.deadline)은 넉넉히 남았지만 슬롯의 일일마감(21:00)은 이미 지남
		ChallengeInfo challenge = challengeInfo(1, D.minusDays(1), D.plusDays(5).atTime(21, 0, 0));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(21, 0, 0)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, D))
				.willReturn(false);

		// When & Then
		assertThatThrownBy(() -> serviceAt(D.atTime(23, 0)).createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("T-U6 평시 낮 제출 — targetDate=오늘로 저장된다(회귀)")
	void normalDaytimeSubmit_targetDateIsToday_regression() {
		// Given — 통상적인 낮 시간 제출, 슬롯=오늘(D)
		ChallengeInfo challenge = challengeInfo(0, D, D.plusDays(3).atTime(23, 59, 59));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, D))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When
		serviceAt(D.atTime(14, 0)).createVerification(command);

		// Then
		ArgumentCaptor<Verification> captor = ArgumentCaptor.forClass(Verification.class);
		verify(verificationRepositoryPort).save(captor.capture());
		assertThat(captor.getValue().getTargetDate()).isEqualTo(D);
	}

	@Test
	@DisplayName("T-U7 PHOTO 크루, D 인증 후(슬롯=D+1) 세션 없이 텍스트 재제출, clock=D 저녁 — V003(하한 우선, V009 아님)")
	void photoCrew_textResubmitWithoutSession_hardGateBeforePhotoRequired() {
		// Given — 가드 순서 고정: 하한(V003)이 PHOTO_REQUIRED(V009)보다 먼저 판정되어야 한다 (step2 §4 nit-1)
		ChallengeInfo challenge = challengeInfo(1, D, D.plusDays(3).atTime(23, 59, 59));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, null, "텍스트 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(23, 59,
				59)));

		// When & Then
		assertThatThrownBy(() -> serviceAt(D.atTime(20, 0)).createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ALREADY_EXISTS);
	}

	@Test
	@DisplayName("T-U9 만료된 세션 + 마감 초과 — V002(세션 상태 검사보다 상한 검증이 우선, 의도된 이중결함 순서 이동)")
	void expiredSessionAndDeadlineExceeded_throwsV002NotSessionExpired() {
		// Given — 세션은 EXPIRED이지만, 그보다 먼저 슬롯 상한(V002)에 걸려야 한다
		ChallengeInfo challenge = challengeInfo(0, D, D.plusDays(3).atTime(21, 0, 0));
		UploadSession expiredSession = UploadSession.of(SESSION_ID, USER_ID, CREW_ID, "images/test.jpg", "image/jpeg",
				UploadSessionStatus.EXPIRED, D.atTime(23, 0), D.atTime(23, 0));
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, CHALLENGE_ID, null, SESSION_ID, "사진 인증");

		given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
		given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID,
				USER_ID)).willReturn(Optional.of(expiredSession));
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("PHOTO", LocalTime.of(21, 0, 0)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, D))
				.willReturn(false);

		// When & Then
		assertThatThrownBy(() -> serviceAt(D.atTime(23, 0)).createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	// ===== 슬롯당 제출 상한(G5) — 취소·수정 도입에 따른 CreateVerificationService 변경분 =====

	@Test
	@DisplayName("슬롯의 findMaxSlotAttempt가 상한 이상이면 V021(슬롯당 제출 상한 초과)")
	void createVerification_slotAttemptLimitReached_throwsV021() {
		// Given — 같은 슬롯에 이미 3건(상한) 존재
		ChallengeInfo challenge = challengeInfo();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.findMaxSlotAttempt(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(3);

		// When & Then
		assertThatThrownBy(() -> createVerificationService.createVerification(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED);

		verify(verificationRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("취소 후 재인증 — findMaxSlotAttempt(1)+1이 새 행의 slotAttempt로 저장된다")
	void createVerification_afterCancel_slotAttemptIncrements() {
		// Given — 취소된 행 1건이 있어 findMaxSlotAttempt가 1을 반환
		ChallengeInfo challenge = challengeInfo();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT", LocalTime.of(23, 59, 59)));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.findMaxSlotAttempt(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(1);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

		// When
		createVerificationService.createVerification(command);

		// Then
		ArgumentCaptor<Verification> captor = ArgumentCaptor.forClass(Verification.class);
		verify(verificationRepositoryPort).save(captor.capture());
		assertThat(captor.getValue().getSlotAttempt()).isEqualTo(2);
	}
}
