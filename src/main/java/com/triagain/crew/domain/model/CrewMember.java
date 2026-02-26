package com.triagain.crew.domain.model;

import com.triagain.crew.domain.vo.CrewRole;

import java.time.LocalDateTime;

public class CrewMember {

    private final String userId;
    private final String crewId;
    private final CrewRole role;
    private final LocalDateTime joinedAt;

    private CrewMember(String userId, String crewId, CrewRole role, LocalDateTime joinedAt) {
        this.userId = userId;
        this.crewId = crewId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    /** 크루장 멤버 생성 — 크루 최초 생성 시 사용 */
    public static CrewMember createLeader(String userId, String crewId) {
        return new CrewMember(
                userId,
                crewId,
                CrewRole.LEADER,
                LocalDateTime.now()
        );
    }

    /** 일반 멤버 생성 — 크루 참여 시 사용 */
    public static CrewMember createMember(String userId, String crewId) {
        return new CrewMember(
                userId,
                crewId,
                CrewRole.MEMBER,
                LocalDateTime.now()
        );
    }

    /** 영속 데이터로 멤버 복원 — DB 조회 결과를 도메인 객체로 변환 */
    public static CrewMember of(String userId, String crewId, CrewRole role, LocalDateTime joinedAt) {
        return new CrewMember(userId, crewId, role, joinedAt);
    }

    /** 크루장 여부 확인 — 권한 검증에 사용 */
    public boolean isLeader() {
        return this.role == CrewRole.LEADER;
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
