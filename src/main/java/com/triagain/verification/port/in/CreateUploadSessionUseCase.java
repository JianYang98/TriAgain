package com.triagain.verification.port.in;

import java.time.LocalDateTime;
import java.util.List;

public interface CreateUploadSessionUseCase {

    UploadSessionResult createUploadSession(CreateUploadSessionCommand command);

    record CreateUploadSessionCommand(String userId, String fileName, String fileType, long fileSize) {
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
