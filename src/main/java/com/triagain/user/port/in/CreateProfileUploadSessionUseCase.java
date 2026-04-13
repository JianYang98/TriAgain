package com.triagain.user.port.in;

import java.time.LocalDateTime;

public interface CreateProfileUploadSessionUseCase {

	/** 프로필 이미지 업로드 세션 생성 — presigned URL 발급 */
	ProfileUploadSessionResult createSession(CreateProfileUploadSessionCommand command);

	record CreateProfileUploadSessionCommand(
			String userId,
			String fileName,
			String fileType,
			long fileSize
	) {
	}

	record ProfileUploadSessionResult(
			String presignedUrl,
			String imageUrl,
			LocalDateTime expiresAt
	) {
	}
}
