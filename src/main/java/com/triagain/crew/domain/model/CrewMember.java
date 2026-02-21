package com.triagain.crew.domain.model;

import com.triagain.crew.domain.vo.CrewRole;

import java.time.LocalDateTime;
import java.util.UUID;

public class CrewMember {

    private final String id;
    private final String userId;
    private final String crewId;
    private final CrewRole role;
    private final LocalDateTime joinedAt;

    private CrewMember(String id, String userId, String crewId, CrewRole role, LocalDateTime joinedAt) {
        this.id = id;
        this.userId = userId;
        this.crewId = crewId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public static CrewMember createLeader(String userId, String crewId) {
        return new CrewMember(
                UUID.randomUUID().toString(),
                userId,
                crewId,
                CrewRole.LEADER,
                LocalDateTime.now()
        );
    }

    public static CrewMember createMember(String userId, String crewId) {
        return new CrewMember(
                UUID.randomUUID().toString(),
                userId,
                crewId,
                CrewRole.MEMBER,
                LocalDateTime.now()
        );
    }

    public static CrewMember of(String id, String userId, String crewId, CrewRole role, LocalDateTime joinedAt) {
        return new CrewMember(id, userId, crewId, role, joinedAt);
    }

    public boolean isLeader() {
        return this.role == CrewRole.LEADER;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCrewId() {
        return crewId;
    }

    public CrewRole getRole() {
        return role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
