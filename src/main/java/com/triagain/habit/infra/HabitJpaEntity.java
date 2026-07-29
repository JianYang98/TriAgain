package com.triagain.habit.infra;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;

@Entity
@Table(name = "habits")
public class HabitJpaEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_type", nullable = false)
	private HabitVerificationType verificationType;

	@Column(name = "deadline_time", nullable = false)
	private LocalTime deadlineTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HabitStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	@Column(name = "verification_content", length = 100)
	private String verificationContent;

	protected HabitJpaEntity() {
	}

	/** JPA 엔티티를 도메인 모델로 변환 */
	public Habit toDomain() {
		return Habit.of(id, userId, name, verificationType, deadlineTime, status, createdAt, endedAt,
				verificationContent);
	}

	/** 도메인 모델을 JPA 엔티티로 변환 — 저장 시 사용 */
	public static HabitJpaEntity fromDomain(Habit habit) {
		HabitJpaEntity entity = new HabitJpaEntity();
		entity.id = habit.getId();
		entity.userId = habit.getUserId();
		entity.name = habit.getName();
		entity.verificationType = habit.getVerificationType();
		entity.deadlineTime = habit.getDeadlineTime();
		entity.status = habit.getStatus();
		entity.createdAt = habit.getCreatedAt();
		entity.endedAt = habit.getEndedAt();
		entity.verificationContent = habit.getVerificationContent();
		return entity;
	}
}
