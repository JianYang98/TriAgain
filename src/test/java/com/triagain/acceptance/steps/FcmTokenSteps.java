package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.NotificationTestAdapter;
import io.cucumber.java.Before;
import io.cucumber.java.ko.만일;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

public class FcmTokenSteps {

	@LocalServerPort
	private int port;

	@Autowired
	private ScenarioContext scenarioContext;

	private NotificationTestAdapter notificationAdapter;

	@Before
	public void setUp() {
		notificationAdapter = new NotificationTestAdapter(port);
	}

	// ===== 조건/만일 =====

	@만일("{string}이 FCM 토큰 {string}을 등록한다")
	public void 사용자가_FCM_토큰을_등록한다(String userId, String fcmToken) {
		Map<String, String> request = Map.of("fcmToken", fcmToken);
		ExtractableResponse<Response> response = notificationAdapter.updateFcmToken(userId, request);
		scenarioContext.setResponse(response);
	}

	@만일("미인증 사용자가 FCM 토큰을 등록한다")
	public void 미인증_사용자가_FCM_토큰을_등록한다() {
		Map<String, String> request = Map.of("fcmToken", "some-token");
		ExtractableResponse<Response> response = notificationAdapter.updateFcmTokenWithoutAuth(request);
		scenarioContext.setResponse(response);
	}
}
