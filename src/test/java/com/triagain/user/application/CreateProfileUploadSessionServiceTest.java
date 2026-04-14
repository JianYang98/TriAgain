package com.triagain.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.user.port.in.CreateProfileUploadSessionUseCase.CreateProfileUploadSessionCommand;

@ExtendWith(MockitoExtension.class)
class CreateProfileUploadSessionServiceTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 4, 14, 14, 0, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(
			FIXED_NOW.atZone(ZONE).toInstant(), ZONE);

	private static final String USER_ID = "user-1";
	private static final String IMAGE_KEY = "profiles/user-1/550e8400-e29b-41d4-a716-446655440000.jpg";
	private static final String PRESIGNED_URL = "https://s3.example.com/presigned";
	private static final String IMAGE_URL = "https://s3.example.com/profiles/user-1/550e8400.jpg";

	@Mock
	private StoragePort storagePort;

	private CreateProfileUploadSessionService service;

	@BeforeEach
	void setUp() {
		service = new CreateProfileUploadSessionService(storagePort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("유효한 jpeg 파일 1MB 업로드 시 presignedUrl, imageUrl, expiresAt 반환")
	void validJpeg_returnsSessionResult() {
		// Given
		given(storagePort.generateImageKey(anyString(), anyString(), anyString()))
				.willReturn(IMAGE_KEY);
		given(storagePort.generatePresignedUrl(anyString(), anyString(), anyLong()))
				.willReturn(PRESIGNED_URL);
		given(storagePort.getImageUrl(IMAGE_KEY)).willReturn(IMAGE_URL);

		CreateProfileUploadSessionCommand command = new CreateProfileUploadSessionCommand(
				USER_ID, "photo.jpg", "image/jpeg", 1024 * 1024);

		// When
		var result = service.createSession(command);

		// Then
		assertThat(result.presignedUrl()).isEqualTo(PRESIGNED_URL);
		assertThat(result.imageUrl()).isEqualTo(IMAGE_URL);
		assertThat(result.expiresAt()).isEqualTo(FIXED_NOW.plusMinutes(15));
	}

	@Test
	@DisplayName("허용되지 않는 파일 타입 application/pdf 시 INVALID_FILE_TYPE 예외")
	void invalidFileType_throwsInvalidFileType() {
		// Given
		CreateProfileUploadSessionCommand command = new CreateProfileUploadSessionCommand(
				USER_ID, "document.pdf", "application/pdf", 1024 * 1024);

		// When & Then
		assertThatThrownBy(() -> service.createSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_FILE_TYPE);
	}

	@Test
	@DisplayName("파일 크기 6MB 초과 시 FILE_TOO_LARGE 예외")
	void fileTooLarge_throwsFileTooLarge() {
		// Given
		long oversizedFile = 6 * 1024 * 1024;
		CreateProfileUploadSessionCommand command = new CreateProfileUploadSessionCommand(
				USER_ID, "photo.jpg", "image/jpeg", oversizedFile);

		// When & Then
		assertThatThrownBy(() -> service.createSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_TOO_LARGE);
	}

	@Test
	@DisplayName("파일 크기 0이면 FILE_TOO_LARGE 예외")
	void fileSizeZero_throwsFileTooLarge() {
		// Given
		CreateProfileUploadSessionCommand command = new CreateProfileUploadSessionCommand(
				USER_ID, "photo.jpg", "image/jpeg", 0);

		// When & Then
		assertThatThrownBy(() -> service.createSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_TOO_LARGE);
	}

	@Test
	@DisplayName("파일 크기 음수면 FILE_TOO_LARGE 예외")
	void fileSizeNegative_throwsFileTooLarge() {
		// Given
		CreateProfileUploadSessionCommand command = new CreateProfileUploadSessionCommand(
				USER_ID, "photo.jpg", "image/jpeg", -1);

		// When & Then
		assertThatThrownBy(() -> service.createSession(command))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_TOO_LARGE);
	}
}
