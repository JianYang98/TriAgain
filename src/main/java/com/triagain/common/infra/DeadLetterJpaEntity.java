package com.triagain.common.infra;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterStatus;
import com.triagain.common.domain.DeadLetterTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letters")
public class DeadLetterJpaEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Enumerated(EnumType.STRING)
	@Column(name = "task_type", nullable = false, length = 30)
	private DeadLetterTaskType taskType;

	@Column(name = "target_id", nullable = false, length = 36)
	private String targetId;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DeadLetterStatus status;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "max_retries", nullable = false)
	private int maxRetries;

	@Column(name = "next_retry_at")
	private LocalDateTime nextRetryAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected DeadLetterJpaEntity() {
	}

	/** JPA 엔티티를 도메인 모델로 변환 */
	public DeadLetter toDomain() {
		return DeadLetter.restore(id, taskType, targetId, errorMessage, status,
				retryCount, maxRetries, nextRetryAt, createdAt, updatedAt);
	}

	/** 도메인 모델을 JPA 엔티티로 변환 — 저장 시 사용 */
	public static DeadLetterJpaEntity fromDomain(DeadLetter deadLetter) {
		DeadLetterJpaEntity entity = new DeadLetterJpaEntity();
		entity.id = deadLetter.getId();
		entity.taskType = deadLetter.getTaskType();
		entity.targetId = deadLetter.getTargetId();
		entity.errorMessage = deadLetter.getErrorMessage();
		entity.status = deadLetter.getStatus();
		entity.retryCount = deadLetter.getRetryCount();
		entity.maxRetries = deadLetter.getMaxRetries();
		entity.nextRetryAt = deadLetter.getNextRetryAt();
		entity.createdAt = deadLetter.getCreatedAt();
		entity.updatedAt = deadLetter.getUpdatedAt();
		return entity;
	}
}
