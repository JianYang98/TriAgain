package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.in.CreateUploadSessionUseCase;
import com.triagain.verification.port.out.StoragePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CreateUploadSessionService implements CreateUploadSessionUseCase {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final StoragePort storagePort;

    @Override
    @Transactional
    public UploadSessionResult createUploadSession(CreateUploadSessionCommand command) {
        validateFileType(command.fileType());
        validateFileSize(command.fileSize());

        String imageKey = storagePort.generateImageKey(command.userId(), command.fileName());

        UploadSession session = UploadSession.create(command.userId(), imageKey, command.fileType());
        UploadSession saved = uploadSessionRepositoryPort.save(session);

        String presignedUrl = storagePort.generatePresignedUrl(imageKey, command.fileType());
        String imageUrl = storagePort.getImageUrl(imageKey);

        return new UploadSessionResult(
                saved.getId(),
                presignedUrl,
                imageUrl,
                LocalDateTime.now().plusMinutes(PRESIGNED_URL_EXPIRY_MINUTES),
                MAX_FILE_SIZE,
                List.copyOf(ALLOWED_TYPES)
        );
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
