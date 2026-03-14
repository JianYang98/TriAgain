package com.triagain.moderation.infra;

import com.triagain.moderation.port.out.VerificationPort;
import com.triagain.verification.port.in.VerificationModerationUseCase;
import com.triagain.verification.port.in.VerificationModerationUseCase.VerificationInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VerificationClientAdapter implements VerificationPort {

    private final VerificationModerationUseCase verificationModerationUseCase;

    @Override
    public void hideVerification(String verificationId) {
        verificationModerationUseCase.hideVerification(verificationId);
    }

    @Override
    public void rejectVerification(String verificationId) {
        verificationModerationUseCase.rejectVerification(verificationId);
    }

    @Override
    public void approveVerification(String verificationId) {
        verificationModerationUseCase.approveVerification(verificationId);
    }

    @Override
    public int incrementReportCount(String verificationId) {
        return verificationModerationUseCase.incrementReportCount(verificationId);
    }

    @Override
    public Optional<VerificationInfo> findVerificationById(String verificationId) {
        return verificationModerationUseCase.findById(verificationId)
                .map(this::toVerificationInfo);
    }

    private VerificationInfo toVerificationInfo(VerificationInfoDto dto) {
        return new VerificationInfo(
                dto.id(),
                dto.challengeId(),
                dto.userId(),
                dto.crewId(),
                dto.reportCount(),
                dto.targetDate()
        );
    }
}