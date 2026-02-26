package com.triagain.crew.infra;

import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "crew_members")
@IdClass(CrewMemberId.class)
public class CrewMemberJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Id
    @Column(name = "crew_id", nullable = false, length = 36)
    private String crewId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrewRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected CrewMemberJpaEntity() {
    }

    /** JPA 엔티티를 도메인 모델로 변환 */
    public CrewMember toDomain() {
        return CrewMember.of(userId, crewId, role, joinedAt);
    }

    /** 도메인 모델을 JPA 엔티티로 변환 — 저장 시 사용 */
    public static CrewMemberJpaEntity fromDomain(CrewMember member) {
        CrewMemberJpaEntity entity = new CrewMemberJpaEntity();
        entity.userId = member.getUserId();
        entity.crewId = member.getCrewId();
        entity.role = member.getRole();
        entity.joinedAt = member.getJoinedAt();
        return entity;
    }
}
