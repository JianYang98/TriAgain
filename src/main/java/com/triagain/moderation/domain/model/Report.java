package com.triagain.moderation.domain.model;

import com.triagain.moderation.domain.vo.ReportReason;
import com.triagain.moderation.domain.vo.ReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

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
            throw new IllegalArgumentException("인증 ID는 필수입니다.");
        }
        if (reporterId == null || reporterId.isBlank()) {
            throw new IllegalArgumentException("신고자 ID는 필수입니다.");
        }
        return new Report(
                UUID.randomUUID().toString(),
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
            throw new IllegalStateException("대기 중인 신고만 처리할 수 있습니다.");
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
