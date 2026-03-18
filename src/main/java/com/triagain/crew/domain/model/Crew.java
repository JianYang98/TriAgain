package com.triagain.crew.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Crew {

    public static final LocalTime DEFAULT_DEADLINE_TIME = LocalTime.of(23, 59, 59);
    public static final String INVITE_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final String id;
    private final String creatorId;
    private String name;
    private String goal;
    private String verificationContent;
    private final VerificationType verificationType;
    private final int maxMembers;
    private int currentMembers;
    private CrewStatus status;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final boolean allowLateJoin;
    private final String inviteCode;
    private final LocalDateTime createdAt;
    private final LocalTime deadlineTime;
    private CrewCategory category;
    private CrewVisibility visibility;
    private final List<CrewMember> members;

    private Crew(String id, String creatorId, String name, String goal,
                 String verificationContent,
                 VerificationType verificationType, int maxMembers,
                 int currentMembers, CrewStatus status, LocalDate startDate,
                 LocalDate endDate, boolean allowLateJoin,
                 String inviteCode, LocalDateTime createdAt,
                 LocalTime deadlineTime, CrewCategory category,
                 CrewVisibility visibility, List<CrewMember> members) {
        this.id = id;
        this.creatorId = creatorId;
        this.name = name;
        this.goal = goal;
        this.verificationContent = verificationContent;
        this.verificationType = verificationType;
        this.maxMembers = maxMembers;
        this.currentMembers = currentMembers;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allowLateJoin = allowLateJoin;
        this.inviteCode = inviteCode;
        this.createdAt = createdAt;
        this.deadlineTime = deadlineTime;
        this.category = category;
        this.visibility = visibility;
        this.members = new ArrayList<>(members);
    }

    /** 새 크루 생성 — 크루장 ID와 설정값으로 초기화 */
    public static Crew create(String creatorId, String name, String goal,
                              String verificationContent,
                              VerificationType verificationType,
                              int maxMembers,
                              LocalDate startDate, LocalDate endDate,
                              boolean allowLateJoin, LocalTime deadlineTime,
                              CrewCategory category, CrewVisibility visibility) {
        Objects.requireNonNull(category, "category must not be null");
        validateMaxMembers(maxMembers);
        validateDates(startDate, endDate);

        String crewId = IdGenerator.generate("CREW");
        CrewMember leader = CrewMember.createLeader(creatorId, crewId);

        return new Crew(
                crewId,
                creatorId,
                name,
                goal,
                verificationContent,
                verificationType,
                maxMembers,
                1,
                CrewStatus.RECRUITING,
                startDate,
                endDate,
                allowLateJoin,
                generateInviteCode(),
                LocalDateTime.now(),
                deadlineTime != null ? deadlineTime : DEFAULT_DEADLINE_TIME,
                category,
                visibility != null ? visibility : CrewVisibility.PRIVATE,
                List.of(leader)
        );
    }

    /** 영속 데이터로 크루 복원 — DB 조회 결과를 도메인 객체로 변환 */
    public static Crew of(String id, String creatorId, String name, String goal,
                          String verificationContent,
                          VerificationType verificationType, int maxMembers,
                          int currentMembers, CrewStatus status, LocalDate startDate,
                          LocalDate endDate, boolean allowLateJoin,
                          String inviteCode, LocalDateTime createdAt,
                          LocalTime deadlineTime, CrewCategory category,
                          CrewVisibility visibility, List<CrewMember> members) {
        return new Crew(id, creatorId, name, goal, verificationContent, verificationType,
                maxMembers, currentMembers, status, startDate,
                endDate, allowLateJoin, inviteCode, createdAt,
                deadlineTime, category, visibility, members);
    }

    /** 멤버 추가 — 정원·상태·마감일 검증 후 멤버 등록 */
    public CrewMember addMember(String userId) {
        if (isFull()) {
            throw new BusinessException(ErrorCode.CREW_FULL);
        }
        if (canNotJoin()) {
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        if (isJoinDeadlinePassed()) {
            throw new BusinessException(ErrorCode.CREW_JOIN_DEADLINE_PASSED);
        }
        if (isAlreadyMember(userId)) {
            throw new BusinessException(ErrorCode.CREW_ALREADY_JOINED);
        }

        CrewMember member = CrewMember.createMember(userId, this.id);
        this.members.add(member);
        this.currentMembers++;
        return member;
    }

    /** 크루 정보 수정 — RECRUITING 상태에서 LEADER만 호출 */
    public void update(String name, String goal, String verificationContent,
                       CrewCategory category, CrewVisibility visibility) {
        if (this.status != CrewStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        if (name != null) this.name = name;
        if (goal != null) this.goal = goal;
        if (verificationContent != null) this.verificationContent = verificationContent;
        if (category != null) this.category = category;
        if (visibility != null) this.visibility = visibility;
    }

    /** 멤버 제거 — RECRUITING 상태에서 MEMBER만 탈퇴 가능 */
    public void removeMember(String userId) {
        if (this.status != CrewStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        CrewMember member = findMemberByUserId(userId);
        if (member.isLeader()) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_LEAVE);
        }
        this.members.remove(member);
        this.currentMembers--;
    }

    /** 유저 ID로 멤버 조회 — 존재하지 않으면 예외 */
    public CrewMember findMemberByUserId(String userId) {
        return this.members.stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_MEMBER_NOT_FOUND));
    }

    /** 삭제 가능 여부 검증 — RECRUITING + 멤버 1명(리더만) */
    public void validateDeletable() {
        if (this.status != CrewStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }
        if (this.currentMembers > 1) {
            throw new BusinessException(ErrorCode.CREW_HAS_MEMBERS);
        }
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
        if (endDate.isBefore(startDate.plusDays(6))) {
            throw new BusinessException(ErrorCode.INVALID_END_DATE);
        }
    }

    private static String generateInviteCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int index = (int) (Math.random() * INVITE_CODE_CHARS.length());
            code.append(INVITE_CODE_CHARS.charAt(index));
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

    public String getVerificationContent() {
        return verificationContent;
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

    public LocalTime getDeadlineTime() {
        return deadlineTime;
    }

    public CrewCategory getCategory() {
        return category;
    }

    public CrewVisibility getVisibility() {
        return visibility;
    }

    /** 공개 크루 여부 확인 — 크루 검색/직접 가입 판단에 사용 */
    public boolean isPublic() {
        return this.visibility == CrewVisibility.PUBLIC;
    }

    public List<CrewMember> getMembers() {
        return Collections.unmodifiableList(members);
    }
}
