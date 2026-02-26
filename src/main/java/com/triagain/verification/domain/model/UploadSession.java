package com.triagain.verification.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.vo.UploadSessionStatus;

import java.time.LocalDateTime;

public class UploadSession {

    private final Long id;
    private final String userId;
    private final String imageKey;
    private final String contentType;
    private UploadSessionStatus status;
    private final LocalDateTime requestedAt;
    private final LocalDateTime createdAt;

    private UploadSession(Long id, String userId, String imageKey, String contentType,
                          UploadSessionStatus status, LocalDateTime requestedAt, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.imageKey = imageKey;
        this.contentType = contentType;
        this.status = status;
        this.requestedAt = requestedAt;
        this.createdAt = createdAt;
    }

    public static UploadSession create(String userId, String imageKey, String contentType) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.USER_ID_REQUIRED);
        }
        if (imageKey == null || imageKey.isBlank()) {
            throw new BusinessException(ErrorCode.IMAGE_KEY_REQUIRED);
        }
        LocalDateTime now = LocalDateTime.now();
        return new UploadSession(null, userId, imageKey, contentType,
                UploadSessionStatus.PENDING, now, now);
    }

    public static UploadSession of(Long id, String userId, String imageKey, String contentType,
                                   UploadSessionStatus status, LocalDateTime requestedAt, LocalDateTime createdAt) {
        return new UploadSession(id, userId, imageKey, contentType, status, requestedAt, createdAt);
    }

    public void complete() {
        if (this.status != UploadSessionStatus.PENDING) {
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_PENDING);
        }
        this.status = UploadSessionStatus.COMPLETED;
    }

    public void expire() {
        if (this.status != UploadSessionStatus.PENDING) {
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_PENDING);
        }
        this.status = UploadSessionStatus.EXPIRED;
    }

    public boolean isPending() {
        return this.status == UploadSessionStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getImageKey() {
        return imageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public UploadSessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
