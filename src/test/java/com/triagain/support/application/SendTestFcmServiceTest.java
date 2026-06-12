package com.triagain.support.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.support.port.in.SendTestFcmUseCase.FcmTestResult;
import com.triagain.support.port.out.NotificationSendPort;

@ExtendWith(MockitoExtension.class)
class SendTestFcmServiceTest {

	@Mock
	private NotificationSendPort notificationSendPort;

	@InjectMocks
	private SendTestFcmService sendTestFcmService;

	private static final String TOKEN = "fcm-token-1";

	@Test
	@DisplayName("send가 true면 SUCCESS를 반환한다")
	void sendTest_success() {
		// Given
		given(notificationSendPort.send(anyString(), anyString(), anyString(), any()))
				.willReturn(true);

		// When
		FcmTestResult result = sendTestFcmService.sendTest(TOKEN);

		// Then
		assertThat(result.sent()).isTrue();
		assertThat(result.status()).isEqualTo("SUCCESS");
	}

	@Test
	@DisplayName("send가 false면 TOKEN_INVALID를 반환한다")
	void sendTest_tokenInvalid() {
		// Given
		given(notificationSendPort.send(anyString(), anyString(), anyString(), any()))
				.willReturn(false);

		// When
		FcmTestResult result = sendTestFcmService.sendTest(TOKEN);

		// Then
		assertThat(result.sent()).isFalse();
		assertThat(result.status()).isEqualTo("TOKEN_INVALID");
	}

	@Test
	@DisplayName("send가 BusinessException을 던지면 ERROR를 반환한다")
	void sendTest_error() {
		// Given
		given(notificationSendPort.send(anyString(), anyString(), anyString(), any()))
				.willThrow(new BusinessException(ErrorCode.FCM_SEND_FAILED));

		// When
		FcmTestResult result = sendTestFcmService.sendTest(TOKEN);

		// Then
		assertThat(result.sent()).isFalse();
		assertThat(result.status()).isEqualTo("ERROR");
		assertThat(result.detail()).isNotBlank();
	}
}
