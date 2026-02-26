package com.triagain.crew.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.vo.ChallengeStatus;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /** 첫 번째 챌린지 생성 — 크루 활성화 시 사이클 1로 시작 */
    public static Challenge createFirst(String userId, String crewId, LocalDate startDate, LocalDateTime deadline) {
        return new Challenge(
                IdGenerator.generate("CHAL"),
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

    /** 다음 사이클 챌린지 생성 — 이전 사이클 완료 후 연속 도전 시 사용 */
    public static Challenge createNext(String userId, String crewId, int previousCycleNumber,
                                       LocalDate startDate, LocalDateTime deadline) {
        return new Challenge(
                IdGenerator.generate("CHAL"),
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

    /** 영속 데이터로 챌린지 복원 — DB 조회 결과를 도메인 객체로 변환 */
    public static Challenge of(String id, String userId, String crewId, int cycleNumber,
                               int targetDays, int completedDays, ChallengeStatus status,
                               LocalDate startDate, LocalDateTime deadline, LocalDateTime createdAt) {
        return new Challenge(id, userId, crewId, cycleNumber, targetDays,
                completedDays, status, startDate, deadline, createdAt);
    }

    /** 인증 완료 기록 — 일일 인증 성공 시 completedDays 증가 */
    public void recordCompletion() {
        if (this.status != ChallengeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }
        this.completedDays++;
        if (this.completedDays >= this.targetDays) {
            this.status = ChallengeStatus.SUCCESS;
        }
    }

    /** 챌린지 실패 처리 — 마감 초과 시 FAILED 상태 전환 */
    public void fail() {
        if (this.status != ChallengeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
        }
        this.status = ChallengeStatus.FAILED;
    }

    /** 마감 초과 여부 확인 — 실패 판정에 사용 */
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
