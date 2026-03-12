package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.in.CreateUploadSessionUseCase.CreateUploadSessionCommand;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CreateUploadSessionServiceTest {

    @Mock
    private UploadSessionRepositoryPort uploadSessionRepositoryPort;

    @Mock
    private StoragePort storagePort;

    @Mock
    private ChallengePort challengePort;

    @InjectMocks
    private CreateUploadSessionService createUploadSessionService;

    private static final String USER_ID = "user-1";
    private static final String CHALLENGE_ID = "challenge-1";
    private static final String CREW_ID = "crew-1";
    private static final String FILE_NAME = "photo.jpg";
    private static final String FILE_TYPE = "image/jpeg";
    private static final long FILE_SIZE = 1024 * 1024; // 1MB
    private static final String IMAGE_KEY = "upload-sessions/user-1/abc123.jpg";
    private static final String PRESIGNED_URL = "https://s3.example.com/presigned";
    private static final String IMAGE_URL = "https://s3.example.com/image.jpg";

    private static ChallengeInfo challengeWithDeadline(LocalDateTime deadline) {
        return new ChallengeInfo(
                CHALLENGE_ID, USER_ID, CREW_ID, 1, 3,
                "IN_PROGRESS", LocalDate.now(), deadline
        );
    }

    private CreateUploadSessionCommand defaultCommand() {
        return new CreateUploadSessionCommand(USER_ID, CHALLENGE_ID, FILE_NAME, FILE_TYPE, FILE_SIZE);
    }

    private void stubStorageAndRepository() {
        given(storagePort.generateImageKey(anyString(), anyString())).willReturn(IMAGE_KEY);
        given(storagePort.generatePresignedUrl(anyString(), anyString())).willReturn(PRESIGNED_URL);
        given(storagePort.getImageUrl(anyString())).willReturn(IMAGE_URL);
        given(uploadSessionRepositoryPort.save(any(UploadSession.class)))
                .willAnswer(invocation -> {
                    UploadSession session = invocation.getArgument(0);
                    return UploadSession.of(1L, session.getUserId(), session.getImageKey(),
                            session.getContentType(), session.getStatus(),
                            session.getRequestedAt(), session.getCreatedAt());
                });
    }

    @Test
    @DisplayName("마감 1분 전 요청 → 성공")
    void deadline_minus1min_success() {
        // Given
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(1);
        given(challengePort.findChallengeById(CHALLENGE_ID))
                .willReturn(Optional.of(challengeWithDeadline(deadline)));
        stubStorageAndRepository();

        // When
        var result = createUploadSessionService.createUploadSession(defaultCommand());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.presignedUrl()).isEqualTo(PRESIGNED_URL);
    }

    @Test
    @DisplayName("정확히 마감 시각 요청 → 성공")
    void deadline_exact_success() {
        // Given — deadline을 아주 약간 미래로 설정 (now 호출 시차 고려)
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(1);
        given(challengePort.findChallengeById(CHALLENGE_ID))
                .willReturn(Optional.of(challengeWithDeadline(deadline)));
        stubStorageAndRepository();

        // When
        var result = createUploadSessionService.createUploadSession(defaultCommand());

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("마감 + 3분 요청 → 성공 (grace period 5분 이내)")
    void deadline_plus3min_withinGrace_success() {
        // Given
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(3);
        given(challengePort.findChallengeById(CHALLENGE_ID))
                .willReturn(Optional.of(challengeWithDeadline(deadline)));
        stubStorageAndRepository();

        // When
        var result = createUploadSessionService.createUploadSession(defaultCommand());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.presignedUrl()).isEqualTo(PRESIGNED_URL);
    }

    @Test
    @DisplayName("마감 + 정확히 5분 요청 → 성공 (경계값 포함)")
    void deadline_plus5min_boundary_success() {
        // Given — 5분 경계에서 약간의 실행 시간 여유 확보
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(5).plusSeconds(2);
        given(challengePort.findChallengeById(CHALLENGE_ID))
                .willReturn(Optional.of(challengeWithDeadline(deadline)));
        stubStorageAndRepository();

        // When
        var result = createUploadSessionService.createUploadSession(defaultCommand());

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("마감 + 6분 요청 → VERIFICATION_DEADLINE_EXCEEDED (grace period 초과)")
    void deadline_plus6min_exceedsGrace_throws() {
        // Given
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(6);
        given(challengePort.findChallengeById(CHALLENGE_ID))
                .willReturn(Optional.of(challengeWithDeadline(deadline)));

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("존재하지 않는 challengeId → CHALLENGE_NOT_FOUND")
    void invalidChallengeId_throws() {
        // Given
        given(challengePort.findChallengeById(CHALLENGE_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(defaultCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("허용되지 않는 파일 타입 → INVALID_FILE_TYPE")
    void invalidFileType_throws() {
        // Given
        LocalDateTime deadline = LocalDateTime.now().plusHours(1);
        given(challengePort.findChallengeById(CHALLENGE_ID))
                .willReturn(Optional.of(challengeWithDeadline(deadline)));
        CreateUploadSessionCommand command = new CreateUploadSessionCommand(
                USER_ID, CHALLENGE_ID, "doc.pdf", "application/pdf", FILE_SIZE);

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
        LocalDateTime deadline = LocalDateTime.now().plusHours(1);
        given(challengePort.findChallengeById(CHALLENGE_ID))
                .willReturn(Optional.of(challengeWithDeadline(deadline)));
        long oversizedFile = 6 * 1024 * 1024; // 6MB (max 5MB)
        CreateUploadSessionCommand command = new CreateUploadSessionCommand(
                USER_ID, CHALLENGE_ID, FILE_NAME, FILE_TYPE, oversizedFile);

        // When & Then
        assertThatThrownBy(() -> createUploadSessionService.createUploadSession(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }
}