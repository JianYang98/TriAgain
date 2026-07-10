package com.triagain.habit.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.util.IdGenerator;

/** 솔로 인증 — crew {@code Verification}의 축소 복제. 피드·신고·검토(moderation) 관련 필드는 전부 제거(step1 §1-3) */
public class HabitVerification {

	private final String id;
	private final String habitCycleId;
	private final String habitId;
	private final String userId;
	private final Long uploadSessionId;
	private final String imageUrl;
	private final String textContent;
	private final LocalDate targetDate;
	private final int attemptNumber;
	private final LocalDateTime createdAt;

	private HabitVerification(String id, String habitCycleId, String habitId, String userId,
			Long uploadSessionId, String imageUrl, String textContent,
			LocalDate targetDate, int attemptNumber, LocalDateTime createdAt) {
		this.id = id;
		this.habitCycleId = habitCycleId;
		this.habitId = habitId;
		this.userId = userId;
		this.uploadSessionId = uploadSessionId;
		this.imageUrl = imageUrl;
		this.textContent = textContent;
		this.targetDate = targetDate;
		this.attemptNumber = attemptNumber;
		this.createdAt = createdAt;
	}

	/** 텍스트 인증 생성 — 콘텐츠 필수 가드(V010) */
	public static HabitVerification createText(String habitCycleId, String habitId, String userId,
			String textContent, LocalDate targetDate, int attemptNumber) {
		if (textContent == null || textContent.isBlank()) {
			throw new BusinessException(ErrorCode.TEXT_CONTENT_REQUIRED);
		}
		return new HabitVerification(
				IdGenerator.generate("HVRF"),
				habitCycleId, habitId, userId,
				null, null, textContent,
				targetDate, attemptNumber,
				LocalDateTime.now()
		);
	}

	/** 사진 인증 생성 — 이미지 URL 필수 가드(V011) */
	public static HabitVerification createPhoto(String habitCycleId, String habitId, String userId,
			Long uploadSessionId, String imageUrl, String textContent,
			LocalDate targetDate, int attemptNumber) {
		if (imageUrl == null || imageUrl.isBlank()) {
			throw new BusinessException(ErrorCode.IMAGE_URL_REQUIRED);
		}
		return new HabitVerification(
				IdGenerator.generate("HVRF"),
				habitCycleId, habitId, userId,
				uploadSessionId, imageUrl, textContent,
				targetDate, attemptNumber,
				LocalDateTime.now()
		);
	}

	/** 영속 데이터로 인증 복원 — DB 조회 결과를 도메인 객체로 변환 */
	public static HabitVerification of(String id, String habitCycleId, String habitId, String userId,
			Long uploadSessionId, String imageUrl, String textContent,
			LocalDate targetDate, int attemptNumber, LocalDateTime createdAt) {
		return new HabitVerification(id, habitCycleId, habitId, userId, uploadSessionId,
				imageUrl, textContent, targetDate, attemptNumber, createdAt);
	}

	public String getId() {
		return id;
	}

	public String getHabitCycleId() {
		return habitCycleId;
	}

	public String getHabitId() {
		return habitId;
	}

	public String getUserId() {
		return userId;
	}

	public Long getUploadSessionId() {
		return uploadSessionId;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public String getTextContent() {
		return textContent;
	}

	public LocalDate getTargetDate() {
		return targetDate;
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
