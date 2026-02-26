package com.triagain.crew.infra;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "challenges")
public class ChallengeJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "crew_id", nullable = false, length = 36)
    private String crewId;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(name = "target_days", nullable = false)
    private int targetDays;

    @Column(name = "completed_days", nullable = false)
    private int completedDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChallengeJpaEntity() {
    }

    /** JPA 엔티티를 도메인 모델로 변환 */
    public Challenge toDomain() {
        return Challenge.of(id, userId, crewId, cycleNumber, targetDays,
                completedDays, status, startDate, deadline, createdAt);
    }

    /** 도메인 모델을 JPA 엔티티로 변환 — 저장 시 사용 */
    public static ChallengeJpaEntity fromDomain(Challenge challenge) {
        ChallengeJpaEntity entity = new ChallengeJpaEntity();
        entity.id = challenge.getId();
        entity.userId = challenge.getUserId();
        entity.crewId = challenge.getCrewId();
        entity.cycleNumber = challenge.getCycleNumber();
        entity.targetDays = challenge.getTargetDays();
        entity.completedDays = challenge.getCompletedDays();
        entity.status = challenge.getStatus();
        entity.startDate = challenge.getStartDate();
        entity.deadline = challenge.getDeadline();
        entity.createdAt = challenge.getCreatedAt();
        return entity;
    }
}
