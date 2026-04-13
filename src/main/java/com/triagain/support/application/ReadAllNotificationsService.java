package com.triagain.support.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.support.port.in.ReadAllNotificationsUseCase;
import com.triagain.support.port.out.NotificationRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReadAllNotificationsService implements ReadAllNotificationsUseCase {

	private final NotificationRepositoryPort notificationRepositoryPort;

	/** 본인의 안 읽은 알림 전체 읽음 처리 */
	@Override
	@Transactional
	public void markAllAsReadByUserId(String userId) {
		notificationRepositoryPort.markAllAsReadByUserId(userId);
	}
}
