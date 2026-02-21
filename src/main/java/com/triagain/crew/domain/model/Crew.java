package com.triagain.crew.domain.model;

import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Crew {

    private final String id;
    private final String creatorId;
    private final String name;
    private final String goal;
    private final VerificationType verificationType;
    private final int minMembers;
    private final int maxMembers;
    private int currentMembers;
    private CrewStatus status;
    private final LocalDate startDate;
    private final String inviteCode;
    private final LocalDateTime createdAt;
    private final List<CrewMember> members;

    private Crew(String id, String creatorId, String name, String goal,
                 VerificationType verificationType, int minMembers, int maxMembers,
                 int currentMembers, CrewStatus status, LocalDate startDate,
                 String inviteCode, LocalDateTime createdAt, List<CrewMember> members) {
        this.id = id;
        this.creatorId = creatorId;
        this.name = name;
        this.goal = goal;
        this.verificationType = verificationType;
        this.minMembers = minMembers;
        this.maxMembers = maxMembers;
        this.currentMembers = currentMembers;
        this.status = status;
        this.startDate = startDate;
        this.inviteCode = inviteCode;
        this.createdAt = createdAt;
        this.members = new ArrayList<>(members);
    }

    public static Crew create(String creatorId, String name, String goal,
                              VerificationType verificationType,
                              int minMembers, int maxMembers,
                              LocalDate startDate) {
        validateMemberRange(minMembers, maxMembers);

        String crewId = UUID.randomUUID().toString();
        CrewMember leader = CrewMember.createLeader(creatorId, crewId);

        return new Crew(
                crewId,
                creatorId,
                name,
                goal,
                verificationType,
                minMembers,
                maxMembers,
                1,
                CrewStatus.RECRUITING,
                startDate,
                generateInviteCode(),
                LocalDateTime.now(),
                List.of(leader)
        );
    }

    public static Crew of(String id, String creatorId, String name, String goal,
                          VerificationType verificationType, int minMembers, int maxMembers,
                          int currentMembers, CrewStatus status, LocalDate startDate,
                          String inviteCode, LocalDateTime createdAt, List<CrewMember> members) {
        return new Crew(id, creatorId, name, goal, verificationType,
                minMembers, maxMembers, currentMembers, status, startDate,
                inviteCode, createdAt, members);
    }

    public CrewMember addMember(String userId) {
        if (!canJoin()) {
            throw new IllegalStateException("크루에 참여할 수 없는 상태입니다.");
        }
        if (isFull()) {
            throw new IllegalStateException("크루 정원이 가득 찼습니다.");
        }
        if (isAlreadyMember(userId)) {
            throw new IllegalStateException("이미 참여 중인 크루입니다.");
        }

        CrewMember member = CrewMember.createMember(userId, this.id);
        this.members.add(member);
        this.currentMembers++;
        return member;
    }

    public boolean isFull() {
        return this.currentMembers >= this.maxMembers;
    }

    public boolean canJoin() {
        return this.status == CrewStatus.RECRUITING && !isFull();
    }

    public void activate() {
        if (this.status != CrewStatus.RECRUITING) {
            throw new IllegalStateException("모집 중인 크루만 활성화할 수 있습니다.");
        }
        this.status = CrewStatus.ACTIVE;
    }

    public void complete() {
        if (this.status != CrewStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 크루만 완료할 수 있습니다.");
        }
        this.status = CrewStatus.COMPLETED;
    }

    private boolean isAlreadyMember(String userId) {
        return this.members.stream()
                .anyMatch(m -> m.getUserId().equals(userId));
    }

    private static void validateMemberRange(int minMembers, int maxMembers) {
        if (minMembers < 2 || maxMembers > 10) {
            throw new IllegalArgumentException("크루 인원은 2~10명이어야 합니다.");
        }
        if (minMembers > maxMembers) {
            throw new IllegalArgumentException("최소 인원이 최대 인원보다 클 수 없습니다.");
        }
    }

    private static String generateInviteCode() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int index = (int) (Math.random() * chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }

    public String getId() {
        return id;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public String getName() {
        return name;
    }

    public String getGoal() {
        return goal;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public int getMinMembers() {
        return minMembers;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public int getCurrentMembers() {
        return currentMembers;
    }

    public CrewStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<CrewMember> getMembers() {
        return Collections.unmodifiableList(members);
    }
}
