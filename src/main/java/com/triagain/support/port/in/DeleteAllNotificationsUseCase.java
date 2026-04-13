package com.triagain.support.port.in;

public interface DeleteAllNotificationsUseCase {

	/** 본인의 알림 전체 삭제 — Hard Delete, 0건이어도 정상 */
	void deleteAllByUserId(String userId);
}
