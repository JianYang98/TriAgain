package com.triagain.support.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.port.in.ReadNotificationUseCase;
import com.triagain.support.port.out.NotificationRepositoryPort;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadNotificationService implements ReadNotificationUseCase {

	private final NotificationRepositoryPort notificationRepositoryPort;

	/** 알림 읽음 처리 — 소유권 검증 후 읽음 상태 변경 */
	@Override
	@Transactional
	public void markAsRead(String notificationId, String userId) {
		Notification notification = notificationRepositoryPort.findById(notificationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

		if (!notification.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
		}

		notification.markAsRead();
		notificationRepositoryPort.save(notification);
	}
}
