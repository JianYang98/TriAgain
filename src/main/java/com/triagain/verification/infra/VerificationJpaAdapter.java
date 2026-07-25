package com.triagain.verification.infra;

import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.VerificationStatus;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    public Verification saveAndFlush(Verification verification) {
        VerificationJpaEntity entity = VerificationJpaEntity.fromDomain(verification);
        return verificationJpaRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Verification> findById(String id) {
        return verificationJpaRepository.findById(id)
                .map(VerificationJpaEntity::toDomain);
    }

    @Override
    public boolean existsActiveByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate) {
        return verificationJpaRepository.existsByUserIdAndCrewIdAndTargetDateAndStatusNot(
                userId, crewId, targetDate, VerificationStatus.CANCELLED);
    }

    @Override
    public Optional<Verification> findActiveByUserIdAndCrewIdAndTargetDate(
            String userId, String crewId, LocalDate targetDate) {
        return verificationJpaRepository.findByUserIdAndCrewIdAndTargetDateAndStatusNot(
                        userId, crewId, targetDate, VerificationStatus.CANCELLED)
                .map(VerificationJpaEntity::toDomain);
    }

    @Override
    public int findMaxSlotAttempt(String userId, String crewId, LocalDate targetDate) {
        return verificationJpaRepository.findMaxSlotAttempt(userId, crewId, targetDate);
    }

    @Override
    public int cancelIfApproved(String verificationId) {
        return verificationJpaRepository.cancelIfApproved(verificationId);
    }

    /** APPROVED 인증 날짜 조회 — LocalDate 직접 반환, 도메인 변환 불필요 */
    @Override
    public List<LocalDate> findApprovedDatesByUserIdAndCrewId(
            String userId, String crewId, LocalDate startDate, LocalDate endDate) {
        return verificationJpaRepository.findApprovedDatesByUserIdAndCrewId(userId, crewId, startDate, endDate);
    }

    @Override
    public Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate) {
        if (crewIds.isEmpty()) return Set.of();
        return new HashSet<>(verificationJpaRepository.findVerifiedCrewIds(userId, crewIds, targetDate));
    }

    /** 유저·크루 묶음별 APPROVED 인증 일수 배치 조회 — 홈 완료 탭 verifiedDayCount 집계에 사용 */
    @Override
    public Map<String, Integer> findApprovedDayCountsByCrewIds(String userId, List<String> crewIds) {
        if (crewIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> results = verificationJpaRepository.countApprovedDaysGroupByCrewId(userId, crewIds);
        Map<String, Integer> map = new HashMap<>();
        for (Object[] row : results) {
            map.put((String) row[0], ((Long) row[1]).intValue());
        }
        return map;
    }

    @Override
    public long countByCrewIdAndTargetDate(String crewId, LocalDate targetDate) {
        return verificationJpaRepository.countByCrewIdAndTargetDate(crewId, targetDate);
    }
}
