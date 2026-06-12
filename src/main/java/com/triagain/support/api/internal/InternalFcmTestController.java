package com.triagain.support.api.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.response.ApiResponse;
import com.triagain.support.port.in.SendTestFcmUseCase;
import com.triagain.support.port.in.SendTestFcmUseCase.FcmTestResult;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/fcm-test")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class InternalFcmTestController {

	private final SendTestFcmUseCase sendTestFcmUseCase;

	/** FCM 키 스모크 테스트 — 단건 발송 후 결과 반환 (firebase.enabled=true 환경 전용) */
	@PostMapping
	public ResponseEntity<ApiResponse<FcmTestResult>> sendTest(@RequestParam String fcmToken) {
		return ResponseEntity.ok(ApiResponse.ok(sendTestFcmUseCase.sendTest(fcmToken)));
	}
}
