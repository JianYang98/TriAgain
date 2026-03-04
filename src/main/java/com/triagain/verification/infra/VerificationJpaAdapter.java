package com.triagain.verification.infra;

import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VerificationJpaAdapter implements VerificationRepositoryPort {

    private final VerificationJpaRepository verificationJpaRepository;

    @Override
    public Verification save(Verification verification) {
        VerificationJpaEntity entity = VerificationJpaEntity.fromDomain(verification);
        return verificationJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Verification> findById(String id) {
        return verificationJpaRepository.findById(id)
                .map(VerificationJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate) {
        return verificationJpaRepository.existsByUserIdAndCrewIdAndTargetDate(userId, crewId, targetDate);
    }

    /** APPROVED 인증 날짜 조회 — LocalDate 직접 반환, 도메인 변환 불필요 */
    @Override
    public List<LocalDate> findApprovedDatesByUserIdAndCrewId(
            String userId, String crewId, LocalDate startDate, LocalDate endDate) {
        return verificationJpaRepository.findApprovedDatesByUserIdAndCrewId(userId, crewId, startDate, endDate);
    }
}
