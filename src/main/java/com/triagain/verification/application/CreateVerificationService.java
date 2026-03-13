package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.DeadlinePolicy;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreateVerificationService implements CreateVerificationUseCase {

    private final VerificationRepositoryPort verificationRepositoryPort;
    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final ChallengePort challengePort;
    private final CrewPort crewPort;
    private final StoragePort storagePort;

    @Override
    @Transactional
    public VerificationResult createVerification(CreateVerificationCommand command) {
        // crewId가 있으면 먼저 멤버십 검증 — 비회원의 크루 상태 노출 + 챌린지 생성 방지
        if (command.crewId() != null) {
            crewPort.validateMembership(command.crewId(), command.userId());
        }

        // photo 인증이고 uploadSessionId가 있을 때, challenge resolve 전에 session cross-crew 선검증
        UploadSession preloadedSession = null;
        if (command.uploadSessionId() != null) {
            preloadedSession = uploadSessionRepositoryPort
                    .findByIdAndUserId(command.uploadSessionId(), command.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

            String targetCrewId = command.crewId();
            if (targetCrewId != null && preloadedSession.getCrewId() != null
                    && !preloadedSession.getCrewId().equals(targetCrewId)) {
                throw new BusinessException(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);
            }
        }

        ChallengeInfo challenge = resolveChallenge(command);

        if (!"IN_PROGRESS".equals(challenge.status())) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }

        // challengeId-only: challenge에서 crewId를 알아낸 후 검증
        if (command.crewId() == null) {
            crewPort.validateMembership(challenge.crewId(), command.userId());
        }

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
            verification = createPhotoVerification(preloadedSession, command, challenge, targetDate);
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

    /** 챌린지 결정 — challengeId/crewId 조합에 따라 조회 또는 생성 */
    private ChallengeInfo resolveChallenge(CreateVerificationCommand command) {
        if (command.challengeId() != null && command.crewId() != null) {
            ChallengeInfo challenge = challengePort.findChallengeById(command.challengeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
            if (!challenge.crewId().equals(command.crewId())) {
                throw new BusinessException(ErrorCode.CHALLENGE_CREW_MISMATCH);
            }
            return challenge;
        }

        if (command.challengeId() != null) {
            return challengePort.findChallengeById(command.challengeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        }

        return challengePort.findOrCreateActiveChallenge(command.userId(), command.crewId());
    }

    /** 사진 인증 생성 — 선조회된 session을 재사용하여 중복 DB 조회 방지 */
    private Verification createPhotoVerification(UploadSession session,
                                                  CreateVerificationCommand command,
                                                  ChallengeInfo challenge,
                                                  LocalDate targetDate) {
        // session.crewId와 challenge.crewId 일치 검증 — command.crewId() 제공 여부와 무관
        if (session.getCrewId() != null && !session.getCrewId().equals(challenge.crewId())) {
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);
        }

        if (!session.isCompleted()) {
            if (session.isPending()) {
                throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_COMPLETED);
            }
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_EXPIRED);
        }

        if (!DeadlinePolicy.isWithinDeadline(session.getRequestedAt(), challenge.deadline())) {
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
        if (!DeadlinePolicy.isWithinDeadline(LocalDateTime.now(), challenge.deadline())) {
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
