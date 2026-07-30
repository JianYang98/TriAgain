package com.triagain.support.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.support.port.in.GetNotificationsUseCase;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.support.port.out.NotificationRepositoryPort.NotificationSlice;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetNotificationsService implements GetNotificationsUseCase {

	private final NotificationRepositoryPort notificationRepositoryPort;

	/** 내 알림 목록 조회 — 최신순 Slice 페이지네이션, isRead 필터 지원 */
	@Override
	@Transactional(readOnly = true)
	public NotificationListResult getNotifications(String userId, Boolean isRead, int page, int size) {
		if (page < 0) page = 0;
		if (size <= 0 || size > 50) size = 20;

		NotificationSlice slice = notificationRepositoryPort.findByUserId(userId, isRead, page, size);
		List<NotificationItem> items = slice.notifications().stream()
				.map(n -> new NotificationItem(
						n.getId(),
						n.getType().name(),
						n.getTitle(),
						n.getContent(),
						n.isRead(),
						n.getTargetType() != null ? n.getTargetType().name() : null,
						n.getTargetId(),
						n.getCreatedAt()))
				.toList();
		return new NotificationListResult(items, slice.hasNext());
	}

	/** 안 읽은 알림 수 조회 — 뱃지 표시용 */
	@Override
	@Transactional(readOnly = true)
	public long getUnreadCount(String userId) {
		return notificationRepositoryPort.countUnreadByUserId(userId);
	}
}
