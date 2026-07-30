package com.triagain.support.port.out;

public interface FcmTokenCleanupPort {

	/** 무효 FCM 토큰 제거 — UNREGISTERED/INVALID_ARGUMENT 감지 시 호출 */
	void clearFcmToken(String userId);
}