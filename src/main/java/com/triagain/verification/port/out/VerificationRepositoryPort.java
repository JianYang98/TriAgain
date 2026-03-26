package com.triagain.verification.port.out;

import com.triagain.verification.domain.model.Verification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface VerificationRepositoryPort {

    Verification save(Verification verification);

    Optional<Verification> findById(String id);

    boolean existsByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate);

    /** APPROVED 인증 날짜 조회 — 크루 기간 범위 내, ASC 정렬 */
    List<LocalDate> findApprovedDatesByUserIdAndCrewId(
            String userId, String crewId, LocalDate startDate, LocalDate endDate);

    /** 오늘 인증 완료한 크루 ID 배치 조회 — N+1 방지용 */
    Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate);
}
