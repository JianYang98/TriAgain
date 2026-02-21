package com.triagain.support.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Reaction {

    private final String id;
    private final String verificationId;
    private final String userId;
    private final String emoji;
    private final LocalDateTime createdAt;

    private Reaction(String id, String verificationId, String userId,
                     String emoji, LocalDateTime createdAt) {
        this.id = id;
        this.verificationId = verificationId;
        this.userId = userId;
        this.emoji = emoji;
        this.createdAt = createdAt;
    }

    public static Reaction create(String verificationId, String userId, String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new IllegalArgumentException("이모지는 필수입니다.");
        }
        return new Reaction(
                UUID.randomUUID().toString(),
                verificationId,
                userId,
                emoji,
                LocalDateTime.now()
        );
    }

    public static Reaction of(String id, String verificationId, String userId,
                              String emoji, LocalDateTime createdAt) {
        return new Reaction(id, verificationId, userId, emoji, createdAt);
    }

    public String getId() {
        return id;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmoji() {
        return emoji;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
