package com.triagain.verification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.triagain.common.port.out.StoragePort;
import com.triagain.verification.application.event.CrewFirstVerificationEvent;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.in.CreateVerificationUseCase.CreateVerificationCommand;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;

/** T1: count==1 경계 — 첫 인증 시 CrewFirstVerificationEvent 1회 발행, 두 번째 미발행 */
@ExtendWith(MockitoExtension.class)
class CrewFirstVerificationServiceTest {

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

	// 실제 시스템 Clock을 감싸는 spy — 아래 challengeInfo()가 LocalDate.now() 기반 동적 값을 쓰므로 무변경으로 통과
	@Spy
	private Clock clock = Clock.systemDefaultZone();

	@Mock
	private VerificationPolicyProperties policyProperties;

	@InjectMocks
	private CreateVerificationService createVerificationService;

	@BeforeEach
	void setUpPolicy() {
		lenient().when(policyProperties.getSlotAttemptLimit()).thenReturn(3);
	}

	private static final String USER_ID = "user-1";
	private static final String CREW_ID = "crew-1";
	private static final String CHALLENGE_ID = "challenge-1";

	private ChallengeInfo challengeInfo() {
		return new ChallengeInfo(
				CHALLENGE_ID, USER_ID, CREW_ID,
				1, 3, "IN_PROGRESS",
				LocalDate.now().minusDays(1),
				LocalDateTime.now().plusHours(2)
		);
	}

	private static CrewVerificationWindowInfo windowInfo(String verificationType) {
		return new CrewVerificationWindowInfo(
				verificationType, "ACTIVE",
				LocalDate.now().minusDays(30), LocalDate.now().plusDays(30),
				false, LocalTime.of(23, 59, 59));
	}

	@Test
	@DisplayName("첫 인증(count==1)이면 CrewFirstVerificationEvent가 1회 발행된다")
	void firstVerification_count1_publishesEvent() {
		// Given
		ChallengeInfo challenge = challengeInfo();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT"));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		// count==1 → 첫 인증
		given(verificationRepositoryPort.countByCrewIdAndTargetDate(CREW_ID, LocalDate.now()))
				.willReturn(1L);

		// When
		createVerificationService.createVerification(command);

		// Then
		verify(eventPublisher).publishEvent(any(CrewFirstVerificationEvent.class));
	}

	@Test
	@DisplayName("두 번째 인증(count==2)이면 CrewFirstVerificationEvent가 발행되지 않는다")
	void secondVerification_count2_doesNotPublishEvent() {
		// Given
		ChallengeInfo challenge = challengeInfo();
		CreateVerificationCommand command = new CreateVerificationCommand(
				USER_ID, null, CREW_ID, null, "텍스트 인증");

		given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
		given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(windowInfo("TEXT"));
		given(verificationRepositoryPort.existsActiveByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
				.willReturn(false);
		given(verificationRepositoryPort.save(any(Verification.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		// count==2 → 첫 인증 아님
		given(verificationRepositoryPort.countByCrewIdAndTargetDate(CREW_ID, LocalDate.now()))
				.willReturn(2L);

		// When
		createVerificationService.createVerification(command);

		// Then
		verify(eventPublisher, never()).publishEvent(any(CrewFirstVerificationEvent.class));
	}
}
