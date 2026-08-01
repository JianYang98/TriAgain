package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.NotificationTestAdapter;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.port.out.NotificationRepositoryPort;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

public class NotificationSteps {

	@LocalServerPort
	private int port;

	@Autowired
	private ScenarioContext scenarioContext;

	@Autowired
	private NotificationRepositoryPort notificationRepositoryPort;

	private NotificationTestAdapter notificationAdapter;

	@Before
	public void setUp() {
		notificationAdapter = new NotificationTestAdapter(port);
	}

	// ===== 조건 (Given) =====

	@조건("{string}에게 알림 {int}개가 존재한다")
	public void 사용자에게_알림이_존재한다(String userId, int count) {
		String lastNotificationId = null;
		for (int i = 0; i < count; i++) {
			Notification notification = Notification.create(
					userId, NotificationType.REMINDER,
					"알림 제목 " + (i + 1), "알림 내용 " + (i + 1),
					NotificationTargetType.CREW, "crew-test");
			Notification saved = notificationRepositoryPort.save(notification);
			lastNotificationId = saved.getId();
		}

		scenarioContext.setNotificationId(lastNotificationId);
	}

	// ===== 만일 (When) =====

	@만일("{string}이 알림 목록을 조회한다")
	public void 사용자가_알림_목록을_조회한다(String userId) {
		ExtractableResponse<Response> response = notificationAdapter.getNotifications(userId);
		scenarioContext.setResponse(response);
	}

	@만일("{string}이 안 읽은 알림 수를 조회한다")
	public void 사용자가_안_읽은_알림_수를_조회한다(String userId) {
		ExtractableResponse<Response> response = notificationAdapter.getUnreadCount(userId);
		scenarioContext.setResponse(response);
	}

	@만일("{string}이 알림을 읽음 처리한다")
	public void 사용자가_알림을_읽음_처리한다(String userId) {
		String notificationId = scenarioContext.getNotificationId();
		ExtractableResponse<Response> response = notificationAdapter.markAsRead(userId, notificationId);
		scenarioContext.setResponse(response);
	}

	@만일("{string}이 존재하지 않는 알림을 읽음 처리한다")
	public void 사용자가_존재하지_않는_알림을_읽음_처리한다(String userId) {
		ExtractableResponse<Response> response = notificationAdapter.markAsRead(userId, "NTFY-nonexistent");
		scenarioContext.setResponse(response);
	}

	@만일("{string}이 {string}의 알림을 읽음 처리한다")
	public void 사용자가_타인의_알림을_읽음_처리한다(String userId, String otherUserId) {
		ExtractableResponse<Response> response = notificationAdapter.markAsRead(userId, scenarioContext.getNotificationId());
		scenarioContext.setResponse(response);
	}

	// ===== 그리고/그러면 (Then) =====

	@그리고("알림 목록 개수는 {int}이다")
	public void 알림_목록_개수는_이다(int expected) {
		int actual = scenarioContext.getResponse().jsonPath().getList("data.notifications").size();
		assertThat(actual).isEqualTo(expected);
	}

	@그리고("안 읽은 알림 수는 {int}이다")
	public void 안_읽은_알림_수는_이다(int expected) {
		int actual = scenarioContext.getResponse().jsonPath().getInt("data.count");
		assertThat(actual).isEqualTo(expected);
	}
}
