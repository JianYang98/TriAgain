package com.triagain.common.domain;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDateTime;

/** 배치 처리 실패 기록 — 재시도 및 운영 모니터링에 사용 */
public class DeadLetter {

	private final String id;
	private final DeadLetterTaskType taskType;
	private final String targetId;
	private final String errorMessage;
	private DeadLetterStatus status;
	private int retryCount;
	private final int maxRetries;
	private LocalDateTime nextRetryAt;
	private final LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	private DeadLetter(String id, DeadLetterTaskType taskType, String targetId, String errorMessage,
					   DeadLetterStatus status, int retryCount, int maxRetries,
					   LocalDateTime nextRetryAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.taskType = taskType;
		this.targetId = targetId;
		this.errorMessage = errorMessage;
		this.status = status;
		this.retryCount = retryCount;
		this.maxRetries = maxRetries;
		this.nextRetryAt = nextRetryAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/** 신규 Dead Letter 생성 — 배치 처리 실패 시 사용 */
	public static DeadLetter of(DeadLetterTaskType taskType, String targetId, String errorMessage) {
		LocalDateTime now = LocalDateTime.now();
		return new DeadLetter(
				IdGenerator.generate("DL"),
				taskType,
				targetId,
				errorMessage,
				DeadLetterStatus.PENDING,
				0,
				3,
				now.plusMinutes(10),
				now,
				now
		);
	}

	/** DB에서 복원 시 사용 */
	public static DeadLetter restore(String id, DeadLetterTaskType taskType, String targetId, String errorMessage,
									 DeadLetterStatus status, int retryCount, int maxRetries,
									 LocalDateTime nextRetryAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
		return new DeadLetter(id, taskType, targetId, errorMessage, status, retryCount, maxRetries,
				nextRetryAt, createdAt, updatedAt);
	}

	/** 재시도 — retryCount 증가 + nextRetryAt 지수 백오프 재계산, maxRetries 초과 시 ABANDONED */
	public void retry() {
		if (this.status != DeadLetterStatus.PENDING) {
			throw new IllegalStateException("PENDING 상태만 재시도 가능");
		}
		this.retryCount++;
		this.updatedAt = LocalDateTime.now();
		if (this.retryCount >= this.maxRetries) {
			this.status = DeadLetterStatus.ABANDONED;
		} else {
			this.nextRetryAt = LocalDateTime.now().plusMinutes(10L * (1L << this.retryCount));
		}
	}

	/** 수동 해결 처리 — PENDING → RESOLVED */
	public void resolve() {
		if (this.status != DeadLetterStatus.PENDING) {
			throw new IllegalStateException("PENDING 상태만 해결 가능");
		}
		this.status = DeadLetterStatus.RESOLVED;
		this.updatedAt = LocalDateTime.now();
	}

	public String getId() { return id; }
	public DeadLetterTaskType getTaskType() { return taskType; }
	public String getTargetId() { return targetId; }
	public String getErrorMessage() { return errorMessage; }
	public DeadLetterStatus getStatus() { return status; }
	public int getRetryCount() { return retryCount; }
	public int getMaxRetries() { return maxRetries; }
	public LocalDateTime getNextRetryAt() { return nextRetryAt; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
