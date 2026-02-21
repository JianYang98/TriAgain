package com.triagain.moderation.infra;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportJpaRepository extends JpaRepository<ReportJpaEntity, String> {

    boolean existsByVerificationIdAndReporterId(String verificationId, String reporterId);
}
