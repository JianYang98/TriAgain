package com.triagain.verification.domain.model;

import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Verification {

    private final String id;
    private final String challengeId;
    private final String userId;
    private final String crewId;
    private final Long uploadSessionId;
    private final String imageUrl;
    private final String textContent;
    private VerificationStatus status;
    private int reportCount;
    private final LocalDate targetDate;
    private final int attemptNumber;
    private ReviewStatus reviewStatus;
    private final LocalDateTime createdAt;

    private Verification(String id, String challengeId, String userId, String crewId,
                         Long uploadSessionId, String imageUrl, String textContent,
                         VerificationStatus status, int reportCount, LocalDate targetDate,
                         int attemptNumber, ReviewStatus reviewStatus, LocalDateTime createdAt) {
        this.id = id;
        this.challengeId = challengeId;
        this.userId = userId;
        this.crewId = crewId;
        this.uploadSessionId = uploadSessionId;
        this.imageUrl = imageUrl;
        this.textContent = textContent;
        this.status = status;
        this.reportCount = reportCount;
        this.targetDate = targetDate;
        this.attemptNumber = attemptNumber;
        this.reviewStatus = reviewStatus;
        this.createdAt = createdAt;
    }

    public static Verification createText(String challengeId, String userId, String crewId,
                                          String textContent, LocalDate targetDate, int attemptNumber) {
        if (textContent == null || textContent.isBlank()) {
            throw new IllegalArgumentException("텍스트 인증 시 텍스트는 필수입니다.");
        }
        return new Verification(
                UUID.randomUUID().toString(),
                challengeId, userId, crewId,
                null, null, textContent,
                VerificationStatus.APPROVED, 0,
                targetDate, attemptNumber,
                ReviewStatus.NOT_REQUIRED,
                LocalDateTime.now()
        );
    }

    public static Verification createPhoto(String challengeId, String userId, String crewId,
                                           Long uploadSessionId, String imageUrl, String textContent,
                                           LocalDate targetDate, int attemptNumber) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("사진 인증 시 이미지 URL은 필수입니다.");
        }
        return new Verification(
                UUID.randomUUID().toString(),
                challengeId, userId, crewId,
                uploadSessionId, imageUrl, textContent,
                VerificationStatus.APPROVED, 0,
                targetDate, attemptNumber,
                ReviewStatus.NOT_REQUIRED,
                LocalDateTime.now()
        );
    }

    public static Verification of(String id, String challengeId, String userId, String crewId,
                                  Long uploadSessionId, String imageUrl, String textContent,
                                  VerificationStatus status, int reportCount, LocalDate targetDate,
                                  int attemptNumber, ReviewStatus reviewStatus, LocalDateTime createdAt) {
        return new Verification(id, challengeId, userId, crewId, uploadSessionId,
                imageUrl, textContent, status, reportCount, targetDate,
                attemptNumber, reviewStatus, createdAt);
    }

    public void incrementReportCount() {
        this.reportCount++;
    }

    public void hide() {
        this.status = VerificationStatus.HIDDEN;
        this.reviewStatus = ReviewStatus.PENDING;
    }

    public void reject() {
        this.status = VerificationStatus.REJECTED;
        this.reviewStatus = ReviewStatus.COMPLETED;
    }

    public void approve() {
        this.status = VerificationStatus.APPROVED;
        this.reviewStatus = ReviewStatus.COMPLETED;
    }

    public String getId() {
        return id;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public String getUserId() {
        return userId;
    }

    public String getCrewId() {
        return crewId;
    }

    public Long getUploadSessionId() {
        return uploadSessionId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getTextContent() {
        return textContent;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public int getReportCount() {
        return reportCount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
