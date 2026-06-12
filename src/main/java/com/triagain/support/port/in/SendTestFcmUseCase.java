package com.triagain.support.port.in;

public interface SendTestFcmUseCase {

	/** FCM 키 스모크 테스트 — 단건 발송으로 서비스계정 키 유효성 점검 */
	FcmTestResult sendTest(String fcmToken);

	/** FCM 스모크 결과 — sent(성공여부), status(SUCCESS|TOKEN_INVALID|ERROR), detail(부연) */
	record FcmTestResult(boolean sent, String status, String detail) { }
}
