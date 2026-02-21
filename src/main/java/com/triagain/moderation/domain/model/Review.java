package com.triagain.moderation.domain.model;

import com.triagain.moderation.domain.vo.ReviewDecision;
import com.triagain.moderation.domain.vo.ReviewerType;

import java.time.LocalDateTime;
import java.util.UUID;

public class Review {

    private final String id;
    private final String reportId;
    private final String reviewerId;
    private final ReviewerType reviewerType;
    private final ReviewDecision decision;
    private final String comment;
    private final LocalDateTime createdAt;

    private Review(String id, String reportId, String reviewerId,
                   ReviewerType reviewerType, ReviewDecision decision,
                   String comment, LocalDateTime createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.reviewerId = reviewerId;
        this.reviewerType = reviewerType;
        this.decision = decision;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public static Review createAutoReview(String reportId, ReviewDecision decision, String comment) {
        return new Review(
                UUID.randomUUID().toString(),
                reportId,
                "SYSTEM",
                ReviewerType.AUTO,
                decision,
                comment,
                LocalDateTime.now()
        );
    }

    public static Review createCrewLeaderReview(String reportId, String reviewerId,
                                                ReviewDecision decision, String comment) {
        return new Review(
                UUID.randomUUID().toString(),
                reportId,
                reviewerId,
                ReviewerType.CREW_LEADER,
                decision,
                comment,
                LocalDateTime.now()
        );
    }

    public static Review of(String id, String reportId, String reviewerId,
                            ReviewerType reviewerType, ReviewDecision decision,
                            String comment, LocalDateTime createdAt) {
        return new Review(id, reportId, reviewerId, reviewerType, decision, comment, createdAt);
    }

    public String getId() {
        return id;
    }

    public String getReportId() {
        return reportId;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public ReviewerType getReviewerType() {
        return reviewerType;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
