package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.in.CreateUploadSessionUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
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
    private final ChallengePort challengePort;

    @Override
    @Transactional
    public UploadSessionResult createUploadSession(CreateUploadSessionCommand command) {
        validateDeadline(command.challengeId());
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

    /** 챌린지 마감 시간 검증 — 마감 이후 업로드 세션 생성 차단 */
    private void validateDeadline(String challengeId) {
        ChallengeInfo challenge = challengePort.findChallengeById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (LocalDateTime.now().isAfter(challenge.deadline())) {
            throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
        }
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
