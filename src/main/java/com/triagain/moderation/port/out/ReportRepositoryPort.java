package com.triagain.moderation.port.out;

import java.util.Optional;

import com.triagain.moderation.domain.model.Report;

public interface ReportRepositoryPort {

	Report save(Report report);

	Optional<Report> findById(String id);

	boolean existsByVerificationIdAndReporterId(String verificationId, String reporterId);
}
