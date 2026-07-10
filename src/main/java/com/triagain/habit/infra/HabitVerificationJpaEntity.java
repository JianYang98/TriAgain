package com.triagain.habit.infra;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.triagain.habit.domain.model.HabitVerification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "habit_verifications")
public class HabitVerificationJpaEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "habit_cycle_id", nullable = false, length = 36)
	private String habitCycleId;

	@Column(name = "habit_id", nullable = false, length = 36)
	private String habitId;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(name = "upload_session_id")
	private Long uploadSessionId;

	@Column(name = "image_url", length = 255)
	private String imageUrl;

	@Column(name = "text_content", length = 500)
	private String textContent;

	@Column(name = "target_date", nullable = false)
	private LocalDate targetDate;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected HabitVerificationJpaEntity() {
	}

	/** JPA 엔티티를 도메인 모델로 변환 */
	public HabitVerification toDomain() {
		return HabitVerification.of(id, habitCycleId, habitId, userId, uploadSessionId,
				imageUrl, textContent, targetDate, attemptNumber, createdAt);
	}

	/** 도메인 모델을 JPA 엔티티로 변환 — 저장 시 사용 */
	public static HabitVerificationJpaEntity fromDomain(HabitVerification verification) {
		HabitVerificationJpaEntity entity = new HabitVerificationJpaEntity();
		entity.id = verification.getId();
		entity.habitCycleId = verification.getHabitCycleId();
		entity.habitId = verification.getHabitId();
		entity.userId = verification.getUserId();
		entity.uploadSessionId = verification.getUploadSessionId();
		entity.imageUrl = verification.getImageUrl();
		entity.textContent = verification.getTextContent();
		entity.targetDate = verification.getTargetDate();
		entity.attemptNumber = verification.getAttemptNumber();
		entity.createdAt = verification.getCreatedAt();
		return entity;
	}
}
