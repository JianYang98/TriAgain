package com.triagain.moderation.port.out;

import com.triagain.moderation.domain.model.Report;

import java.util.Optional;

public interface ReportRepositoryPort {

    Report save(Report report);

    Optional<Report> findById(String id);

    boolean existsByVerificationIdAndReporterId(String verificationId, String reporterId);
}
