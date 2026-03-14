package com.triagain.verification.application;

import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.in.VerificationModerationUseCase;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Moderation Context 전용 인증 상태 제어 서비스 */
@Service
@RequiredArgsConstructor
public class VerificationModerationService implements VerificationModerationUseCase {

    private final VerificationRepositoryPort verificationRepositoryPort;

    @Override
    @Transactional
    public void hideVerification(String verificationId) {
        verificationRepositoryPort.findById(verificationId)
                .ifPresent(verification -> {
                    verification.hide();
                    verificationRepositoryPort.save(verification);
                });
    }

    @Override
    @Transactional
    public void rejectVerification(String verificationId) {
        verificationRepositoryPort.findById(verificationId)
                .ifPresent(verification -> {
                    verification.reject();
                    verificationRepositoryPort.save(verification);
                });
    }

    @Override
    @Transactional
    public void approveVerification(String verificationId) {
        verificationRepositoryPort.findById(verificationId)
                .ifPresent(verification -> {
                    verification.approve();
                    verificationRepositoryPort.save(verification);
                });
    }

    @Override
    @Transactional
    public int incrementReportCount(String verificationId) {
        return verificationRepositoryPort.findById(verificationId)
                .map(verification -> {
                    verification.incrementReportCount();
                    verificationRepositoryPort.save(verification);
                    return verification.getReportCount();
                })
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationInfoDto> findById(String verificationId) {
        return verificationRepositoryPort.findById(verificationId)
                .map(this::toDto);
    }

    private VerificationInfoDto toDto(Verification verification) {
        return new VerificationInfoDto(
                verification.getId(),
                verification.getChallengeId(),
                verification.getUserId(),
                verification.getCrewId(),
                verification.getReportCount(),
                verification.getTargetDate()
        );
    }
}