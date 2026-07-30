package com.triagain.support.domain.model;

import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDateTime;

public class Notification {

	private final String id;
	private final String userId;
	private final NotificationType type;
	private final String title;
	private final String content;
	private boolean isRead;
	private final NotificationTargetType targetType;
	private final String targetId;
	private final LocalDateTime createdAt;

	private Notification(String id, String userId, NotificationType type,
						String title, String content, boolean isRead,
						NotificationTargetType targetType, String targetId,
						LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.type = type;
		this.title = title;
		this.content = content;
		this.isRead = isRead;
		this.targetType = targetType;
		this.targetId = targetId;
		this.createdAt = createdAt;
	}

	/** 타겟 없는 알림 생성 — 일반 알림용 */
	public static Notification create(String userId, NotificationType type,
									String title, String content) {
		return new Notification(
				IdGenerator.generate("NTFY"),
				userId,
				type,
				title,
				content,
				false,
				null,
				null,
				LocalDateTime.now()
		);
	}

	/** 타겟이 있는 알림 생성 — 크루/인증/챌린지 연결 알림용 */
	public static Notification create(String userId, NotificationType type,
									String title, String content,
									NotificationTargetType targetType, String targetId) {
		return new Notification(
				IdGenerator.generate("NTFY"),
				userId,
				type,
				title,
				content,
				false,
				targetType,
				targetId,
				LocalDateTime.now()
		);
	}

	/** JPA 엔티티 → 도메인 모델 복원용 */
	public static Notification of(String id, String userId, NotificationType type,
								String title, String content, boolean isRead,
								NotificationTargetType targetType, String targetId,
								LocalDateTime createdAt) {
		return new Notification(id, userId, type, title, content, isRead,
				targetType, targetId, createdAt);
	}

	public void markAsRead() {
		this.isRead = true;
	}

	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public boolean isRead() {
		return isRead;
	}

	public NotificationTargetType getTargetType() {
		return targetType;
	}

	public String getTargetId() {
		return targetId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
