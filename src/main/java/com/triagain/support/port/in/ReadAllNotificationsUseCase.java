package com.triagain.support.port.in;

public interface ReadAllNotificationsUseCase {

	/** 본인의 안 읽은 알림 전체 읽음 처리 — 0건이어도 정상 */
	void markAllAsReadByUserId(String userId);
}
