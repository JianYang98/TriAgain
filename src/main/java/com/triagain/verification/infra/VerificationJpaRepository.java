package com.triagain.verification.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VerificationJpaRepository extends JpaRepository<VerificationJpaEntity, String> {

    boolean existsByUserIdAndCrewIdAndTargetDate(String userId, String crewId, LocalDate targetDate);

    /** APPROVED 인증 날짜 조회 — 크루 기간 범위 내, ASC 정렬 */
    @Query("SELECT v.targetDate FROM VerificationJpaEntity v " +
           "WHERE v.userId = :userId AND v.crewId = :crewId " +
           "AND v.status = 'APPROVED' " +
           "AND v.targetDate BETWEEN :startDate AND :endDate " +
           "ORDER BY v.targetDate ASC")
    List<LocalDate> findApprovedDatesByUserIdAndCrewId(
            @Param("userId") String userId,
            @Param("crewId") String crewId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
