package com.triagain.support.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.support.port.in.DeleteAllNotificationsUseCase;
import com.triagain.support.port.out.NotificationRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeleteAllNotificationsService implements DeleteAllNotificationsUseCase {

	private final NotificationRepositoryPort notificationRepositoryPort;

	/** 본인의 알림 전체 삭제 */
	@Override
	@Transactional
	public void deleteAllByUserId(String userId) {
		notificationRepositoryPort.deleteAllByUserId(userId);
	}
}
