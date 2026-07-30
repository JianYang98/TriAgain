package com.triagain.verification.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.vo.UploadSessionStatus;

import java.time.LocalDateTime;

public class UploadSession {

	private final Long id;
	private final String userId;
	private final String crewId;
	private final String habitId;
	private final String imageKey;
	private final String contentType;
	private UploadSessionStatus status;
	private final LocalDateTime requestedAt;
	private final LocalDateTime createdAt;

	private UploadSession(Long id, String userId, String crewId, String habitId, String imageKey, String contentType,
						UploadSessionStatus status, LocalDateTime requestedAt, LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.crewId = crewId;
		this.habitId = habitId;
		this.imageKey = imageKey;
		this.contentType = contentType;
		this.status = status;
		this.requestedAt = requestedAt;
		this.createdAt = createdAt;
	}

	/** 크루 세션 생성 — habitId는 null로 저장(step2 §9 XOR). 기존 크루 호출부(거동 불변) 대응 */
	public static UploadSession create(String userId, String crewId, String imageKey, String contentType) {
		return create(userId, crewId, null, imageKey, contentType);
	}

	/** 크루/솔로 세션 생성 — crewId·habitId 중 발급 컨텍스트에 해당하는 쪽만 채워짐(XOR, step2 §9) */
	public static UploadSession create(String userId, String crewId, String habitId,
									String imageKey, String contentType) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException(ErrorCode.USER_ID_REQUIRED);
		}
		if (imageKey == null || imageKey.isBlank()) {
			throw new BusinessException(ErrorCode.IMAGE_KEY_REQUIRED);
		}
		LocalDateTime now = LocalDateTime.now();
		return new UploadSession(null, userId, crewId, habitId, imageKey, contentType,
				UploadSessionStatus.PENDING, now, now);
	}

	/** 레거시 8-arg 복원 팩토리 — habitId 없는 기존 크루 전용 호출부 하위 호환(habitId=null) */
	public static UploadSession of(Long id, String userId, String crewId, String imageKey, String contentType,
								UploadSessionStatus status, LocalDateTime requestedAt, LocalDateTime createdAt) {
		return of(id, userId, crewId, null, imageKey, contentType, status, requestedAt, createdAt);
	}

	public static UploadSession of(Long id, String userId, String crewId, String habitId,
								String imageKey, String contentType,
								UploadSessionStatus status, LocalDateTime requestedAt, LocalDateTime createdAt) {
		return new UploadSession(id, userId, crewId, habitId, imageKey, contentType, status, requestedAt, createdAt);
	}

	/** 업로드 세션 완료 처리 — Lambda 콜백 시 호출, 이미 COMPLETED면 멱등 처리 */
	public void complete() {
		if (this.status == UploadSessionStatus.COMPLETED) {
			return;
		}
		if (this.status != UploadSessionStatus.PENDING) {
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_PENDING);
		}
		this.status = UploadSessionStatus.COMPLETED;
	}

	/**
	 * 업로드 세션 만료 처리 — 스케줄러가 PENDING 세션을 일괄 만료할 때 호출.
	 * complete()와 달리 의도적으로 비멱등: 스케줄러는 PENDING만 조회하므로 중복 호출 없음.
	 * (complete()는 Lambda at-least-once 재전송 대비 멱등 처리)
	 */
	public void expire() {
		if (this.status != UploadSessionStatus.PENDING) {
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_PENDING);
		}
		this.status = UploadSessionStatus.EXPIRED;
	}

	public boolean isPending() {
		return this.status == UploadSessionStatus.PENDING;
	}

	public boolean isCompleted() {
		return this.status == UploadSessionStatus.COMPLETED;
	}

	public Long getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getCrewId() {
		return crewId;
	}

	public String getHabitId() {
		return habitId;
	}

	public String getImageKey() {
		return imageKey;
	}

	public String getContentType() {
		return contentType;
	}

	public UploadSessionStatus getStatus() {
		return status;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
