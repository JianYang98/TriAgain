package com.triagain.moderation.infra;

import com.triagain.moderation.port.out.VerificationPort;
import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VerificationClientAdapter implements VerificationPort {

    private final VerificationRepositoryPort verificationRepositoryPort;

    @Override
    public void hideVerification(String verificationId) {
        verificationRepositoryPort.findById(verificationId)
                .ifPresent(verification -> {
                    verification.hide();
                    verificationRepositoryPort.save(verification);
                });
    }

    @Override
    public void rejectVerification(String verificationId) {
        verificationRepositoryPort.findById(verificationId)
                .ifPresent(verification -> {
                    verification.reject();
                    verificationRepositoryPort.save(verification);
                });
    }

    @Override
    public void approveVerification(String verificationId) {
        verificationRepositoryPort.findById(verificationId)
                .ifPresent(verification -> {
                    verification.approve();
                    verificationRepositoryPort.save(verification);
                });
    }

    @Override
    public Optional<VerificationInfo> findVerificationById(String verificationId) {
        return verificationRepositoryPort.findById(verificationId)
                .map(this::toVerificationInfo);
    }

    private VerificationInfo toVerificationInfo(Verification verification) {
        return new VerificationInfo(
                verification.getId(),
                verification.getChallengeId(),
                verification.getUserId(),
                verification.getCrewId(),
                verification.getReportCount(),
                verification.getTargetDate()
        );
    }
}
