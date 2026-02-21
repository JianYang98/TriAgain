package com.triagain.support.domain.model;

import com.triagain.support.domain.vo.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {

    private final String id;
    private final String userId;
    private final NotificationType type;
    private final String title;
    private final String content;
    private boolean isRead;
    private final LocalDateTime createdAt;

    private Notification(String id, String userId, NotificationType type,
                         String title, String content, boolean isRead,
                         LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public static Notification create(String userId, NotificationType type,
                                      String title, String content) {
        return new Notification(
                UUID.randomUUID().toString(),
                userId,
                type,
                title,
                content,
                false,
                LocalDateTime.now()
        );
    }

    public static Notification of(String id, String userId, NotificationType type,
                                  String title, String content, boolean isRead,
                                  LocalDateTime createdAt) {
        return new Notification(id, userId, type, title, content, isRead, createdAt);
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isRead() {
        return isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
