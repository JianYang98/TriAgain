package com.triagain.crew.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Crew {

    private final String id;
    private final String creatorId;
    private final String name;
    private final String goal;
    private final VerificationType verificationType;
    private final int maxMembers;
    private int currentMembers;
    private CrewStatus status;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final boolean allowLateJoin;
    private final String inviteCode;
    private final LocalDateTime createdAt;
    private final List<CrewMember> members;

    private Crew(String id, String creatorId, String name, String goal,
                 VerificationType verificationType, int maxMembers,
                 int currentMembers, CrewStatus status, LocalDate startDate,
                 LocalDate endDate, boolean allowLateJoin,
                 String inviteCode, LocalDateTime createdAt, List<CrewMember> members) {
        this.id = id;
        this.creatorId = creatorId;
        this.name = name;
        this.goal = goal;
        this.verificationType = verificationType;
        this.maxMembers = maxMembers;
        this.currentMembers = currentMembers;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allowLateJoin = allowLateJoin;
        this.inviteCode = inviteCode;
        this.createdAt = createdAt;
        this.members = new ArrayList<>(members);
    }

    /** 새 크루 생성 — 크루장 ID와 설정값으로 초기화 */
    public static Crew create(String creatorId, String name, String goal,
                              VerificationType verificationType,
                              int maxMembers,
                              LocalDate startDate, LocalDate endDate,
                              boolean allowLateJoin) {
        validateMaxMembers(maxMembers);
        validateDates(startDate, endDate);

        String crewId = IdGenerator.generate("CREW");
        CrewMember leader = CrewMember.createLeader(creatorId, crewId); // 리더 생성

        return new Crew(
                crewId,
                creatorId,
                name,
                goal,
                verificationType,
                maxMembers,
                1,
                CrewStatus.RECRUITING,
                startDate,
                endDate,
                allowLateJoin,
                generateInviteCode(),
                LocalDateTime.now(),
                List.of(leader)
        );
    }

    /** 영속 데이터로 크루 복원 — DB 조회 결과를 도메인 객체로 변환 */
    public static Crew of(String id, String creatorId, String name, String goal,
                          VerificationType verificationType, int maxMembers,
                          int currentMembers, CrewStatus status, LocalDate startDate,
                          LocalDate endDate, boolean allowLateJoin,
                          String inviteCode, LocalDateTime createdAt, List<CrewMember> members) {
        return new Crew(id, creatorId, name, goal, verificationType,
                maxMembers, currentMembers, status, startDate,
                endDate, allowLateJoin, inviteCode, createdAt, members);
    }

    /** 멤버 추가 — 정원·상태·마감일 검증 후 멤버 등록 */
    public CrewMember addMember(String userId) {
        if (canNotJoin()) {
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        if (isFull()) {
            throw new BusinessException(ErrorCode.CREW_FULL);
        }
        if (isAlreadyMember(userId)) {
            throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
        }

        CrewMember member = CrewMember.createMember(userId, this.id);
        this.members.add(member);
        this.currentMembers++;
        return member;
    }

    /** 정원 초과 여부 확인 — 참여 가능 판단에 사용 */
    public boolean isFull() {
        return this.currentMembers >= this.maxMembers;
    }

     /** 참여 가능 상태 확인 — 모집 중이고 정원 미달인지 판단 */
    public boolean canJoin() {
        if (isFull()) return false;
        if (status == CrewStatus.RECRUITING) return true;
        return status == CrewStatus.ACTIVE && allowLateJoin;
    }

    /** 참여 상태 확인 - 불가 **/
    public boolean canNotJoin() {
        return !canJoin();
    }

    /** 참여 마감 여부 확인 — 시작일 이후 늦은 참여 차단에 사용 */
    public boolean isJoinDeadlinePassed() {
        return LocalDate.now().isAfter(endDate.minusDays(3));
    }

    /** 크루 활성화 — RECRUITING → ACTIVE 상태 전환 */
    public void activate() {
        if (this.status != CrewStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        this.status = CrewStatus.ACTIVE;
    }

    /** 크루 종료 — ACTIVE → COMPLETED 상태 전환 */
    public void complete() {
        if (this.status != CrewStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CREW_NOT_ACTIVE);
        }
        this.status = CrewStatus.COMPLETED;
    }

    private boolean isAlreadyMember(String userId) {
        return this.members.stream()
                .anyMatch(m -> m.getUserId().equals(userId));
    }

    private static void validateMaxMembers(int maxMembers) {
        if (maxMembers < 1 || maxMembers > 10) {
            throw new BusinessException(ErrorCode.INVALID_MAX_MEMBERS);
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_START_DATE);
        }
        if (!endDate.isAfter(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_END_DATE);
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

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isAllowLateJoin() {
        return allowLateJoin;
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
