package com.triagain.crew.domain.model;

import com.triagain.crew.domain.vo.ChallengeStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Challenge {

    private static final int DEFAULT_TARGET_DAYS = 3;

    private final String id;
    private final String userId;
    private final String crewId;
    private final int cycleNumber;
    private final int targetDays;
    private int completedDays;
    private ChallengeStatus status;
    private final LocalDate startDate;
    private final LocalDateTime deadline;
    private final LocalDateTime createdAt;

    private Challenge(String id, String userId, String crewId, int cycleNumber,
                      int targetDays, int completedDays, ChallengeStatus status,
                      LocalDate startDate, LocalDateTime deadline, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.crewId = crewId;
        this.cycleNumber = cycleNumber;
        this.targetDays = targetDays;
        this.completedDays = completedDays;
        this.status = status;
        this.startDate = startDate;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }

    public static Challenge createFirst(String userId, String crewId, LocalDate startDate, LocalDateTime deadline) {
        return new Challenge(
                UUID.randomUUID().toString(),
                userId,
                crewId,
                1,
                DEFAULT_TARGET_DAYS,
                0,
                ChallengeStatus.IN_PROGRESS,
                startDate,
                deadline,
                LocalDateTime.now()
        );
    }

    public static Challenge createNext(String userId, String crewId, int previousCycleNumber,
                                       LocalDate startDate, LocalDateTime deadline) {
        return new Challenge(
                UUID.randomUUID().toString(),
                userId,
                crewId,
                previousCycleNumber + 1,
                DEFAULT_TARGET_DAYS,
                0,
                ChallengeStatus.IN_PROGRESS,
                startDate,
                deadline,
                LocalDateTime.now()
        );
    }

    public static Challenge of(String id, String userId, String crewId, int cycleNumber,
                               int targetDays, int completedDays, ChallengeStatus status,
                               LocalDate startDate, LocalDateTime deadline, LocalDateTime createdAt) {
        return new Challenge(id, userId, crewId, cycleNumber, targetDays,
                completedDays, status, startDate, deadline, createdAt);
    }

    public void recordCompletion() {
        if (this.status != ChallengeStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 챌린지만 인증을 기록할 수 있습니다.");
        }
        this.completedDays++;
        if (this.completedDays >= this.targetDays) {
            this.status = ChallengeStatus.SUCCESS;
        }
    }

    public void fail() {
        if (this.status != ChallengeStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 챌린지만 실패 처리할 수 있습니다.");
        }
        this.status = ChallengeStatus.FAILED;
    }

    public boolean isDeadlineExceeded() {
        return LocalDateTime.now().isAfter(this.deadline);
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

    public int getCycleNumber() {
        return cycleNumber;
    }

    public int getTargetDays() {
        return targetDays;
    }

    public int getCompletedDays() {
        return completedDays;
    }

    public ChallengeStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
