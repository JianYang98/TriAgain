package com.triagain.moderation.infra;

import com.triagain.moderation.domain.model.Review;
import com.triagain.moderation.domain.vo.ReviewDecision;
import com.triagain.moderation.domain.vo.ReviewerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class ReviewJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "report_id", nullable = false, length = 36)
    private String reportId;

    @Column(name = "reviewer_id", nullable = false, length = 36)
    private String reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_type", nullable = false)
    private ReviewerType reviewerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewDecision decision;

    @Column
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReviewJpaEntity() {
    }

    public Review toDomain() {
        return Review.of(id, reportId, reviewerId, reviewerType, decision, comment, createdAt);
    }

    public static ReviewJpaEntity fromDomain(Review review) {
        ReviewJpaEntity entity = new ReviewJpaEntity();
        entity.id = review.getId();
        entity.reportId = review.getReportId();
        entity.reviewerId = review.getReviewerId();
        entity.reviewerType = review.getReviewerType();
        entity.decision = review.getDecision();
        entity.comment = review.getComment();
        entity.createdAt = review.getCreatedAt();
        return entity;
    }
}
