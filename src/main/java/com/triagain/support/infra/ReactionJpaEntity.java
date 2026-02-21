package com.triagain.support.infra;

import com.triagain.support.domain.model.Reaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "reactions")
public class ReactionJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "verification_id", nullable = false, length = 36)
    private String verificationId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private String emoji;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReactionJpaEntity() {
    }

    public Reaction toDomain() {
        return Reaction.of(id, verificationId, userId, emoji, createdAt);
    }

    public static ReactionJpaEntity fromDomain(Reaction reaction) {
        ReactionJpaEntity entity = new ReactionJpaEntity();
        entity.id = reaction.getId();
        entity.verificationId = reaction.getVerificationId();
        entity.userId = reaction.getUserId();
        entity.emoji = reaction.getEmoji();
        entity.createdAt = reaction.getCreatedAt();
        return entity;
    }
}
