package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.in.CreateVerificationUseCase.CreateVerificationCommand;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.StoragePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @InjectMocks
    private CreateVerificationService createVerificationService;

    private static final String USER_ID = "user-1";
    private static final String CREW_ID = "crew-1";
    private static final String CHALLENGE_ID = "challenge-1";
    private static final Long SESSION_ID = 1L;

    private static ChallengeInfo challengeInfo() {
        return new ChallengeInfo(
                CHALLENGE_ID, USER_ID, CREW_ID,
                2, 3, "IN_PROGRESS",
                LocalDate.now().minusDays(2),
                LocalDateTime.now().plusHours(1)
        );
    }

    private static UploadSession completedSession() {
        return UploadSession.of(SESSION_ID, USER_ID, "images/test.jpg", "image/jpeg",
                UploadSessionStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now());
    }

    private static UploadSession usedSession() {
        UploadSession session = completedSession();
        session.use();
        return session;
    }

    @Test
    @DisplayName("사진 인증 성공 시 세션 상태가 USED로 전환된다")
    void createPhotoVerification_success_sessionUsed() {
        // Given
        ChallengeInfo challenge = challengeInfo();
        UploadSession session = completedSession();
        CreateVerificationCommand command = new CreateVerificationCommand(
                USER_ID, CHALLENGE_ID, null, SESSION_ID, "오늘도 완료!");

        given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
        given(crewPort.getVerificationType(CREW_ID)).willReturn("PHOTO");
        given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID, USER_ID))
                .willReturn(Optional.of(session));
        given(storagePort.getImageUrl("images/test.jpg")).willReturn("https://cdn.example.com/images/test.jpg");
        given(verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
                .willReturn(false);
        given(verificationRepositoryPort.save(any(Verification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When
        createVerificationService.createVerification(command);

        // Then
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.USED);
        verify(uploadSessionRepositoryPort).save(session);
    }

    @Test
    @DisplayName("USED 세션으로 인증 시도 시 UPLOAD_SESSION_ALREADY_USED 예외")
    void createPhotoVerification_usedSession_throwsAlreadyUsed() {
        // Given
        ChallengeInfo challenge = challengeInfo();
        UploadSession session = usedSession();
        CreateVerificationCommand command = new CreateVerificationCommand(
                USER_ID, CHALLENGE_ID, null, SESSION_ID, "재사용 시도");

        given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.of(challenge));
        given(crewPort.getVerificationType(CREW_ID)).willReturn("PHOTO");
        given(uploadSessionRepositoryPort.findByIdAndUserId(SESSION_ID, USER_ID))
                .willReturn(Optional.of(session));
        given(verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
                .willReturn(false);

        // When & Then
        assertThatThrownBy(() -> createVerificationService.createVerification(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_SESSION_ALREADY_USED);
    }

    @Test
    @DisplayName("crewId만으로 인증 시 findOrCreateActiveChallenge 호출")
    void createVerification_crewIdOnly_usesAutoCreate() {
        // Given
        ChallengeInfo challenge = challengeInfo();
        CreateVerificationCommand command = new CreateVerificationCommand(
                USER_ID, null, CREW_ID, null, "텍스트 인증");

        given(challengePort.findOrCreateActiveChallenge(USER_ID, CREW_ID)).willReturn(challenge);
        given(crewPort.getVerificationType(CREW_ID)).willReturn("TEXT");
        given(verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
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
        given(crewPort.getVerificationType(CREW_ID)).willReturn("TEXT");
        given(verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
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
        given(crewPort.getVerificationType(CREW_ID)).willReturn("TEXT");
        given(verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(USER_ID, CREW_ID, LocalDate.now()))
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
}
