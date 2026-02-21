package com.triagain.verification.infra;

import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_session")
public class UploadSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Column(name = "content_type")
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadSessionStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected UploadSessionJpaEntity() {
    }

    public UploadSession toDomain() {
        return UploadSession.of(id, userId, imageKey, contentType, status, requestedAt, createdAt);
    }

    public static UploadSessionJpaEntity fromDomain(UploadSession session) {
        UploadSessionJpaEntity entity = new UploadSessionJpaEntity();
        entity.id = session.getId();
        entity.userId = session.getUserId();
        entity.imageKey = session.getImageKey();
        entity.contentType = session.getContentType();
        entity.status = session.getStatus();
        entity.requestedAt = session.getRequestedAt();
        entity.createdAt = session.getCreatedAt();
        return entity;
    }
}
