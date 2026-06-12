package com.triagain.support.application;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.triagain.support.port.in.SendTestFcmUseCase;
import com.triagain.support.port.out.NotificationSendPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SendTestFcmService implements SendTestFcmUseCase {

	private static final String TITLE = "FCM 스모크 테스트";
	private static final String BODY = "키 검증용 발송";
	private static final Map<String, String> DATA = Map.of("type", "TEST");

	private final NotificationSendPort notificationSendPort;

	/** FCM 키 스모크 테스트 — 단건 발송 후 성공/토큰무효/에러 결과 반환 */
	@Override
	public FcmTestResult sendTest(String fcmToken) {
		try {
			boolean sent = notificationSendPort.send(fcmToken, TITLE, BODY, DATA);
			return sent
					? new FcmTestResult(true, "SUCCESS", "발송 성공")
					: new FcmTestResult(false, "TOKEN_INVALID", "토큰 영구 무효(UNREGISTERED/INVALID_ARGUMENT)");
		} catch (Exception e) {
			return new FcmTestResult(false, "ERROR", e.getMessage());
		}
	}
}
