package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.in.CreateVerificationUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.ChallengePort.ChallengeInfo;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CreateVerificationService implements CreateVerificationUseCase {

    private final VerificationRepositoryPort verificationRepositoryPort;
    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final ChallengePort challengePort;

    @Override
    @Transactional
    public VerificationResult createVerification(CreateVerificationCommand command) {
        ChallengeInfo challenge = challengePort.findChallengeById(command.challengeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        LocalDate targetDate = LocalDate.now();

        if (verificationRepositoryPort.existsByUserIdAndCrewIdAndTargetDate(
                command.userId(), challenge.crewId(), targetDate)) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_EXISTS);
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

        if (!session.isPending()) {
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_COMPLETED);
        }

        if (session.getRequestedAt().isAfter(challenge.deadline())) {
            throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
        }

        Verification verification = Verification.createPhoto(
                challenge.id(),
                command.userId(),
                challenge.crewId(),
                session.getId(),
                command.imageUrl(),
                command.textContent(),
                targetDate,
                challenge.completedDays() + 1
        );

        session.complete();
        uploadSessionRepositoryPort.save(session);

        return verification;
    }

    private Verification createTextVerification(CreateVerificationCommand command,
                                                 ChallengeInfo challenge,
                                                 LocalDate targetDate) {
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
