package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.in.CreateVerificationUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.StoragePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreateVerificationService implements CreateVerificationUseCase {

    private static final Duration GRACE_PERIOD = Duration.ofMinutes(5);

    private final VerificationRepositoryPort verificationRepositoryPort;
    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final ChallengePort challengePort;
    private final CrewPort crewPort;
    private final StoragePort storagePort;

    @Override
    @Transactional
    public VerificationResult createVerification(CreateVerificationCommand command) {
        ChallengeInfo challenge = challengePort.findChallengeById(command.challengeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        crewPort.validateMembership(challenge.crewId(), command.userId());

        LocalDate targetDate = LocalDate.now();

        if (verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(
                command.userId(), challenge.crewId(), targetDate)) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_EXISTS);
        }

        String verificationType = crewPort.getVerificationType(challenge.crewId());
        if ("PHOTO".equals(verificationType) && command.uploadSessionId() == null) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }

        Verification verification;

        if (command.uploadSessionId() != null) {
            verification = createPhotoVerification(command, challenge, targetDate);
        } else {
            verification = createTextVerification(command, challenge, targetDate);
        }

        Verification saved = verificationRepositoryPort.save(verification);

        challengePort.recordCompletion(challenge.id());

        return new VerificationResult(
                saved.getId(),
                saved.getChallengeId(),
                saved.getUserId(),
                saved.getCrewId(),
                saved.getImageUrl(),
                saved.getTextContent(),
                saved.getStatus(),
                saved.getReviewStatus(),
                saved.getReportCount(),
                saved.getTargetDate(),
                saved.getCreatedAt()
        );
    }

    private Verification createPhotoVerification(CreateVerificationCommand command,
                                                  ChallengeInfo challenge,
                                                  LocalDate targetDate) {
        UploadSession session = uploadSessionRepositoryPort
                .findByIdAndUserId(command.uploadSessionId(), command.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

        if (!session.isCompleted()) {
            if (session.isPending()) {
                throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_COMPLETED);
            }
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_EXPIRED);
        }

        if (session.getRequestedAt().isAfter(challenge.deadline().plus(GRACE_PERIOD))) {
            throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
        }

        String imageUrl = storagePort.getImageUrl(session.getImageKey());

        return Verification.createPhoto(
                challenge.id(),
                command.userId(),
                challenge.crewId(),
                session.getId(),
                imageUrl,
                command.textContent(),
                targetDate,
                challenge.completedDays() + 1
        );
    }

    private Verification createTextVerification(CreateVerificationCommand command,
                                                 ChallengeInfo challenge,
                                                 LocalDate targetDate) {
        if (LocalDateTime.now().isAfter(challenge.deadline())) {
            throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
        }

        return Verification.createText(
                challenge.id(),
                command.userId(),
                challenge.crewId(),
                command.textContent(),
                targetDate,
                challenge.completedDays() + 1
        );
    }
}
