package com.triagain.moderation.infra;

import com.triagain.moderation.domain.model.Report;
import com.triagain.moderation.domain.vo.ReportReason;
import com.triagain.moderation.domain.vo.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"verification_id", "reporter_id"})
})
public class ReportJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "verification_id", nullable = false, length = 36)
    private String verificationId;

    @Column(name = "reporter_id", nullable = false, length = 36)
    private String reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReportJpaEntity() {
    }

    public Report toDomain() {
        return Report.of(id, verificationId, reporterId, reason, status, description, createdAt);
    }

    public static ReportJpaEntity fromDomain(Report report) {
        ReportJpaEntity entity = new ReportJpaEntity();
        entity.id = report.getId();
        entity.verificationId = report.getVerificationId();
        entity.reporterId = report.getReporterId();
        entity.reason = report.getReason();
        entity.status = report.getStatus();
        entity.description = report.getDescription();
        entity.createdAt = report.getCreatedAt();
        return entity;
    }
}
