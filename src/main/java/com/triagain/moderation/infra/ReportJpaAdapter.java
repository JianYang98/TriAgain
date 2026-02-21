package com.triagain.moderation.infra;

import com.triagain.moderation.domain.model.Report;
import com.triagain.moderation.port.out.ReportRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportJpaAdapter implements ReportRepositoryPort {

    private final ReportJpaRepository reportJpaRepository;

    @Override
    public Report save(Report report) {
        ReportJpaEntity entity = ReportJpaEntity.fromDomain(report);
        return reportJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Report> findById(String id) {
        return reportJpaRepository.findById(id)
                .map(ReportJpaEntity::toDomain);
    }

    @Override
    public boolean existsByVerificationIdAndReporterId(String verificationId, String reporterId) {
        return reportJpaRepository.existsByVerificationIdAndReporterId(verificationId, reporterId);
    }
}
