package com.triagain.support.infra;

import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class NotificationJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 50)
    private NotificationTargetType targetType;

    @Column(name = "target_id", length = 36)
    private String targetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected NotificationJpaEntity() {
    }

    public Notification toDomain() {
        return Notification.of(id, userId, type, title, content, isRead,
                targetType, targetId, createdAt);
    }

    public static NotificationJpaEntity fromDomain(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.id = notification.getId();
        entity.userId = notification.getUserId();
        entity.type = notification.getType();
        entity.title = notification.getTitle();
        entity.content = notification.getContent();
        entity.isRead = notification.isRead();
        entity.targetType = notification.getTargetType();
        entity.targetId = notification.getTargetId();
        entity.createdAt = notification.getCreatedAt();
        return entity;
    }
}
