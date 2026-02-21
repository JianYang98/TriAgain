package com.triagain.crew.infra;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

@Entity
@Table(name = "crews")
public class CrewJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "creator_id", nullable = false, length = 36)
    private String creatorId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false)
    private VerificationType verificationType;

    @Column(name = "min_members", nullable = false)
    private int minMembers;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "current_members", nullable = false)
    private int currentMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrewStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "invite_code", nullable = false, unique = true, length = 6)
    private String inviteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CrewJpaEntity() {
    }

    public Crew toDomain() {
        return Crew.of(id, creatorId, name, goal, verificationType,
                minMembers, maxMembers, currentMembers, status, startDate,
                inviteCode, createdAt, Collections.emptyList());
    }

    public Crew toDomainWithMembers(java.util.List<CrewMemberJpaEntity> memberEntities) {
        var members = memberEntities.stream()
                .map(CrewMemberJpaEntity::toDomain)
                .toList();
        return Crew.of(id, creatorId, name, goal, verificationType,
                minMembers, maxMembers, currentMembers, status, startDate,
                inviteCode, createdAt, members);
    }

    public static CrewJpaEntity fromDomain(Crew crew) {
        CrewJpaEntity entity = new CrewJpaEntity();
        entity.id = crew.getId();
        entity.creatorId = crew.getCreatorId();
        entity.name = crew.getName();
        entity.goal = crew.getGoal();
        entity.verificationType = crew.getVerificationType();
        entity.minMembers = crew.getMinMembers();
        entity.maxMembers = crew.getMaxMembers();
        entity.currentMembers = crew.getCurrentMembers();
        entity.status = crew.getStatus();
        entity.startDate = crew.getStartDate();
        entity.inviteCode = crew.getInviteCode();
        entity.createdAt = crew.getCreatedAt();
        return entity;
    }

    public String getId() {
        return id;
    }
}
