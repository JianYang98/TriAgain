package com.triagain.habit.infra;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "habit_cycles")
public class HabitCycleJpaEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "habit_id", nullable = false, length = 36)
	private String habitId;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(name = "cycle_number", nullable = false)
	private int cycleNumber;

	@Column(name = "target_days", nullable = false)
	private int targetDays;

	@Column(name = "completed_days", nullable = false)
	private int completedDays;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HabitCycleStatus status;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDateTime deadline;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected HabitCycleJpaEntity() {
	}

	/** JPA 엔티티를 도메인 모델로 변환 */
	public HabitCycle toDomain() {
		return HabitCycle.of(id, habitId, userId, cycleNumber, targetDays,
				completedDays, status, startDate, deadline, createdAt);
	}

	/** 도메인 모델을 JPA 엔티티로 변환 — 저장 시 사용 */
	public static HabitCycleJpaEntity fromDomain(HabitCycle cycle) {
		HabitCycleJpaEntity entity = new HabitCycleJpaEntity();
		entity.id = cycle.getId();
		entity.habitId = cycle.getHabitId();
		entity.userId = cycle.getUserId();
		entity.cycleNumber = cycle.getCycleNumber();
		entity.targetDays = cycle.getTargetDays();
		entity.completedDays = cycle.getCompletedDays();
		entity.status = cycle.getStatus();
		entity.startDate = cycle.getStartDate();
		entity.deadline = cycle.getDeadline();
		entity.createdAt = cycle.getCreatedAt();
		return entity;
	}
}
