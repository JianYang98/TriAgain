package com.triagain.habit.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.util.IdGenerator;
import com.triagain.habit.domain.vo.HabitCycleStatus;

/** 작심 사이클 — crew {@code Challenge}의 경량 복제. 캡 없는 무기한 습관 컨테이너 대응(step1 §1-2) */
public class HabitCycle {

	public static final int DEFAULT_TARGET_DAYS = 3;

	private final String id;
	private final String habitId;
	private final String userId;
	private final int cycleNumber;
	private final int targetDays;
	private int completedDays;
	private HabitCycleStatus status;
	private final LocalDate startDate;
	private final LocalDateTime deadline;
	private final LocalDateTime createdAt;

	private HabitCycle(String id, String habitId, String userId, int cycleNumber, int targetDays,
			int completedDays, HabitCycleStatus status, LocalDate startDate,
			LocalDateTime deadline, LocalDateTime createdAt) {
		this.id = id;
		this.habitId = habitId;
		this.userId = userId;
		this.cycleNumber = cycleNumber;
		this.targetDays = targetDays;
		this.completedDays = completedDays;
		this.status = status;
		this.startDate = startDate;
		this.deadline = deadline;
		this.createdAt = createdAt;
	}

	/** 사이클 시작(첫 시작/재시작 통합) — 첫/재시작 구분은 cycleNumber 값뿐(D3, step4 §2) */
	public static HabitCycle start(String habitId, String userId, int cycleNumber,
			LocalDate startDate, LocalDateTime deadline) {
		return new HabitCycle(
				IdGenerator.generate("HCYC"),
				habitId,
				userId,
				cycleNumber,
				DEFAULT_TARGET_DAYS,
				0,
				HabitCycleStatus.IN_PROGRESS,
				startDate,
				deadline,
				LocalDateTime.now()
		);
	}

	/** 영속 데이터로 사이클 복원 — DB 조회 결과를 도메인 객체로 변환 */
	public static HabitCycle of(String id, String habitId, String userId, int cycleNumber,
			int targetDays, int completedDays, HabitCycleStatus status, LocalDate startDate,
			LocalDateTime deadline, LocalDateTime createdAt) {
		return new HabitCycle(id, habitId, userId, cycleNumber, targetDays,
				completedDays, status, startDate, deadline, createdAt);
	}

	/** 인증 완료 기록 — completedDays 증가, targetDays 도달 시 SUCCESS 전환 */
	public void recordCompletion() {
		if (this.status != HabitCycleStatus.IN_PROGRESS) {
			throw new BusinessException(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
		}
		this.completedDays++;
		if (this.completedDays >= this.targetDays) {
			this.status = HabitCycleStatus.SUCCESS;
		}
	}

	/** 사이클 실패 처리 — 마감 초과 미인증(스케줄러) 또는 습관 종료 시 IN_PROGRESS를 정리할 때 사용 */
	public void fail() {
		if (this.status != HabitCycleStatus.IN_PROGRESS) {
			throw new BusinessException(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
		}
		this.status = HabitCycleStatus.FAILED;
	}

	public String getId() {
		return id;
	}

	public String getHabitId() {
		return habitId;
	}

	public String getUserId() {
		return userId;
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

	public HabitCycleStatus getStatus() {
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
