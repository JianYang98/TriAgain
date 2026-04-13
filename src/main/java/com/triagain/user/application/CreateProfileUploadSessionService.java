package com.triagain.user.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.port.in.CreateProfileUploadSessionUseCase;
import com.triagain.verification.port.out.StoragePort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateProfileUploadSessionService implements CreateProfileUploadSessionUseCase {

	private static final Set<String> ALLOWED_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp"
	);
	private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
	private static final int PRESIGNED_URL_EXPIRY_MINUTES = 15;
	private static final String PROFILE_PREFIX = "profiles";

	private final StoragePort storagePort;
	private final Clock clock;

	/** 프로필 이미지 presigned URL 발급 — 파일 타입/크기 검증 후 S3 경로 생성 */
	@Override
	public ProfileUploadSessionResult createSession(CreateProfileUploadSessionCommand command) {
		validateFileType(command.fileType());
		validateFileSize(command.fileSize());

		String imageKey = storagePort.generateImageKey(
				PROFILE_PREFIX, command.userId(), command.fileName());
		String presignedUrl = storagePort.generatePresignedUrl(
				imageKey, command.fileType());
		String imageUrl = storagePort.getImageUrl(imageKey);

		return new ProfileUploadSessionResult(
				presignedUrl, imageUrl,
				LocalDateTime.now(clock).plusMinutes(PRESIGNED_URL_EXPIRY_MINUTES));
	}

	private void validateFileType(String fileType) {
		if (!ALLOWED_TYPES.contains(fileType)) {
			throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
		}
	}

	private void validateFileSize(long fileSize) {
		if (fileSize > MAX_FILE_SIZE) {
			throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
		}
	}
}
