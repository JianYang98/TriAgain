package com.triagain.moderation.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.moderation.domain.vo.ReportReason;
import com.triagain.moderation.domain.vo.ReportStatus;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDateTime;

public class Report {

    private final String id;
    private final String verificationId;
    private final String reporterId;
    private final ReportReason reason;
    private ReportStatus status;
    private final String description;
    private final LocalDateTime createdAt;

    private Report(String id, String verificationId, String reporterId,
                   ReportReason reason, ReportStatus status, String description,
                   LocalDateTime createdAt) {
        this.id = id;
        this.verificationId = verificationId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static Report create(String verificationId, String reporterId,
                                ReportReason reason, String description) {
        if (verificationId == null || verificationId.isBlank()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ID_REQUIRED);
        }
        if (reporterId == null || reporterId.isBlank()) {
            throw new BusinessException(ErrorCode.REPORTER_ID_REQUIRED);
        }
        return new Report(
                IdGenerator.generate("REPT"),
                verificationId,
                reporterId,
                reason,
                ReportStatus.PENDING,
                description,
                LocalDateTime.now()
        );
    }

    public static Report of(String id, String verificationId, String reporterId,
                            ReportReason reason, ReportStatus status,
                            String description, LocalDateTime createdAt) {
        return new Report(id, verificationId, reporterId, reason, status, description, createdAt);
    }

    public void approve() {
        validatePending();
        this.status = ReportStatus.APPROVED;
    }

    public void reject() {
        validatePending();
        this.status = ReportStatus.REJECTED;
    }

    public void expire() {
        validatePending();
        this.status = ReportStatus.EXPIRED;
    }

    private void validatePending() {
        if (this.status != ReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }
    }

    public String getId() {
        return id;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
