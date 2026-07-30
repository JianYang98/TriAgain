package com.triagain.verification.port.in;

import java.time.LocalDateTime;
import java.util.List;

public interface CreateUploadSessionUseCase {

	UploadSessionResult createUploadSession(CreateUploadSessionCommand command);

	/** crewId/habitId는 XOR — 크루 인증용 세션은 crewId, 솔로 인증용 세션은 habitId(step2 §9) */
	record CreateUploadSessionCommand(
			String userId, String crewId, String habitId, String fileName, String fileType, long fileSize) {
	}

	record UploadSessionResult(
			Long uploadSessionId,
			String presignedUrl,
			String imageUrl,
			LocalDateTime expiresAt,
			long maxFileSize,
			List<String> allowedTypes
	) {
	}
}
