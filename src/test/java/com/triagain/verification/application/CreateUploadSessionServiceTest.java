package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.in.CreateUploadSessionUseCase.CreateUploadSessionCommand;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ActiveChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewVerificationWindowInfo;
import com.triagain.verification.port.out.StoragePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class CreateUploadSessionServiceTest {

    @Mock
    private UploadSessionRepositoryPort uploadSessionRepositoryPort;

    @Mock
    private StoragePort storagePort;

    @Mock
    private ChallengePort challengePort;

    @Mock
    private CrewPort crewPort;

    @InjectMocks
    private CreateUploadSessionService createUploadSessionService;

    private static final String USER_ID = "user-1";
    private static final String CREW_ID = "crew-1";
    private static final String CHALLENGE_ID = "challenge-1";
    private static final String FILE_NAME = "photo.jpg";
    private static final String FILE_TYPE = "image/jpeg";
    private static final long FILE_SIZE = 1024 * 1024; // 1MB
    private static final String IMAGE_KEY = "upload-sessions/user-1/abc123.jpg";
    private static final String PRESIGNED_URL = "https://s3.example.com/presigned";
    private static final String IMAGE_URL = "https://s3.example.com/image.jpg";

    private static CrewVerificationWindowInfo activePhotoCrew(LocalTime deadlineTime) {
        return new CrewVerificationWindowInfo(
                "PHOTO", "ACTIVE",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                false, deadlineTime
        );
    }

    private static ActiveChallengeInfo activeChallengeWithDeadline(LocalDateTime deadline) {
        return new ActiveChallengeInfo(CHALLENGE_ID, "IN_PROGRESS", 1, 3, deadline);
    }

    private CreateUploadSessionCommand defaultCommand() {
        return new CreateUploadSessionCommand(USER_ID, CREW_ID, FILE_NAME, FILE_TYPE, FILE_SIZE);
    }

    private void stubMembershipAndCrewInfo(CrewVerificationWindowInfo crewInfo) {
        doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
        given(crewPort.getCrewVerificationWindowInfo(CREW_ID)).willReturn(crewInfo);
    }

    private void stubStorageAndRepository() {
        given(storagePort.generateImageKey(anyString(), anyString())).willReturn(IMAGE_KEY);
        given(storagePort.generatePresignedUrl(anyString(), anyString())).willReturn(PRESIGNED_URL);
        given(storagePort.getImageUrl(anyString())).willReturn(IMAGE_URL);
        given(uploadSessionRepositoryPort.save(any(UploadSession.class)))
                .willAnswer(invocation -> {
                    UploadSession session = invocation.getArgument(0);
                    return UploadSession.of(1L, session.getUserId(), session.getCrewId(),
                            session.getImageKey(), session.getContentType(),
                            session.getStatus(), session.getRequestedAt(), session.getCreatedAt());
                });
    }

    @Test
    @DisplayName("활성 챌린지 있고 마감 전 → 성공")
    void activeChallengeBeforeDeadline_success() {
        // Given
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(30);
        stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
        given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
                .willReturn(Optional.of(activeChallengeWithDeadline(deadline)));
        stubStorageAndRepository();

        // When
        var result = createUploadSessionService.createUploadSession(defaultCommand());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.presignedUrl()).isEqualTo(PRESIGNED_URL);
    }

    @Test
    @DisplayName("활성 챌린지 있고 마감 + 3분 → 성공 (grace period 5분 이내)")
    void activeChallengeWithinGrace_success() {
        // Given
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(3);
        stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
        given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
                .willReturn(Optional.of(activeChallengeWithDeadline(deadline)));
        stubStorageAndRepository();

        // When
        var result = createUploadSessionService.createUploadSession(defaultCommand());

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("활성 챌린지 있고 마감 + 6분 → VERIFICATION_DEADLINE_EXCEEDED (grace 초과)")
    void activeChallengeExceedsGrace_throws() {
        // Given
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(6);
        stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
        given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
                .willReturn(Optional.of(activeChallengeWithDeadline(deadline)));

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("활성 챌린지 없고 크루 마감 전 → 성공 (crew-level deadline)")
    void noActiveChallengeBeforeCrewDeadline_success() {
        // Given — deadlineTime을 23:59:59로 설정하면 오늘 마감 전
        stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
        given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
                .willReturn(Optional.empty());
        stubStorageAndRepository();

        // When
        var result = createUploadSessionService.createUploadSession(defaultCommand());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.presignedUrl()).isEqualTo(PRESIGNED_URL);
    }

    @Test
    @DisplayName("활성 챌린지 없고 크루 마감 후 → VERIFICATION_DEADLINE_EXCEEDED")
    void noActiveChallengeAfterCrewDeadline_throws() {
        // Given — deadlineTime을 이미 지난 시각으로 설정
        LocalTime pastDeadline = LocalTime.now().minusMinutes(10);
        stubMembershipAndCrewInfo(activePhotoCrew(pastDeadline));
        given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("비활성 크루 → CREW_NOT_ACTIVE")
    void inactiveCrew_throws() {
        // Given
        doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
        given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
                .willReturn(new CrewVerificationWindowInfo(
                        "PHOTO", "RECRUITING",
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(10),
                        false, LocalTime.of(23, 59, 59)
                ));

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_ACTIVE);
    }

    @Test
    @DisplayName("크루 기간 종료 → CREW_PERIOD_ENDED")
    void crewPeriodEnded_throws() {
        // Given
        doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
        given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
                .willReturn(new CrewVerificationWindowInfo(
                        "PHOTO", "ACTIVE",
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1),
                        false, LocalTime.of(23, 59, 59)
                ));

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_PERIOD_ENDED);
    }

    @Test
    @DisplayName("크루 시작 전 → CREW_NOT_STARTED")
    void crewNotStarted_throws() {
        // Given
        doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
        given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
                .willReturn(new CrewVerificationWindowInfo(
                        "PHOTO", "ACTIVE",
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(10),
                        false, LocalTime.of(23, 59, 59)
                ));

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_NOT_STARTED);
    }

    @Test
    @DisplayName("비회원 → CREW_ACCESS_DENIED")
    void nonMember_throws() {
        // Given
        doThrow(new BusinessException(ErrorCode.CREW_ACCESS_DENIED))
                .when(crewPort).validateMembership(CREW_ID, USER_ID);

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREW_ACCESS_DENIED);
    }

    @Test
    @DisplayName("TEXT 크루 → UPLOAD_SESSION_NOT_REQUIRED")
    void textCrew_throws() {
        // Given
        doNothing().when(crewPort).validateMembership(CREW_ID, USER_ID);
        given(crewPort.getCrewVerificationWindowInfo(CREW_ID))
                .willReturn(new CrewVerificationWindowInfo(
                        "TEXT", "ACTIVE",
                        LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                        false, LocalTime.of(23, 59, 59)
                ));

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_REQUIRED);
    }

    @Test
    @DisplayName("허용되지 않는 파일 타입 → INVALID_FILE_TYPE")
    void invalidFileType_throws() {
        // Given
        stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
        given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
                .willReturn(Optional.of(activeChallengeWithDeadline(LocalDateTime.now().plusHours(1))));
        CreateUploadSessionCommand command = new CreateUploadSessionCommand(
                USER_ID, CREW_ID, "doc.pdf", "application/pdf", FILE_SIZE);

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    @DisplayName("파일 크기 초과 → FILE_TOO_LARGE")
    void fileTooLarge_throws() {
        // Given
        stubMembershipAndCrewInfo(activePhotoCrew(LocalTime.of(23, 59, 59)));
        given(challengePort.findActiveByUserIdAndCrewId(USER_ID, CREW_ID))
                .willReturn(Optional.of(activeChallengeWithDeadline(LocalDateTime.now().plusHours(1))));
        long oversizedFile = 6 * 1024 * 1024; // 6MB (max 5MB)
        CreateUploadSessionCommand command = new CreateUploadSessionCommand(
                USER_ID, CREW_ID, FILE_NAME, FILE_TYPE, oversizedFile);

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }
}
