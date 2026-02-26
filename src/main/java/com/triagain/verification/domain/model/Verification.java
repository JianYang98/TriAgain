package com.triagain.verification.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.vo.ReviewStatus;
import com.triagain.verification.domain.vo.VerificationStatus;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
            throw new BusinessException(ErrorCode.TEXT_CONTENT_REQUIRED);
        }
        return new Verification(
                IdGenerator.generate("VRFY"),
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
            throw new BusinessException(ErrorCode.IMAGE_URL_REQUIRED);
        }
        return new Verification(
                IdGenerator.generate("VRFY"),
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
