package com.triagain.verification.infra;

import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "verifications", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "crew_id", "target_date"})
})
public class VerificationJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "challenge_id", nullable = false, length = 36)
    private String challengeId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "crew_id", nullable = false, length = 36)
    private String crewId;

    @Column(name = "upload_session_id")
    private Long uploadSessionId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "text_content")
    private String textContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected VerificationJpaEntity() {
    }

    public Verification toDomain() {
        return Verification.of(id, challengeId, userId, crewId, uploadSessionId,
                imageUrl, textContent, status, reportCount, targetDate,
                attemptNumber, reviewStatus, createdAt);
    }

    public static VerificationJpaEntity fromDomain(Verification verification) {
        VerificationJpaEntity entity = new VerificationJpaEntity();
        entity.id = verification.getId();
        entity.challengeId = verification.getChallengeId();
        entity.userId = verification.getUserId();
        entity.crewId = verification.getCrewId();
        entity.uploadSessionId = verification.getUploadSessionId();
        entity.imageUrl = verification.getImageUrl();
        entity.textContent = verification.getTextContent();
        entity.status = verification.getStatus();
        entity.reportCount = verification.getReportCount();
        entity.targetDate = verification.getTargetDate();
        entity.attemptNumber = verification.getAttemptNumber();
        entity.reviewStatus = verification.getReviewStatus();
        entity.createdAt = verification.getCreatedAt();
        return entity;
    }
}
