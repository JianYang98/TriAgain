package com.triagain.user.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;

/**
 * AppleOAuthAdapter 단위 테스트 — @PostConstruct fail-fast 동작 검증.
 * 외부 API 호출(/auth/token, /auth/revoke)은 통합 테스트 영역이므로 본 테스트는
 * 환경변수 검증 + .p8 키 파싱 + disabled 동작에 집중한다.
 */
class AppleOAuthAdapterTest {

	private static final String CLIENT_ID = "com.triagain.triagain";
	private static final String TEAM_ID = "ABCD123456";
	private static final String KEY_ID = "KEY1234567";

	/** 테스트용 ES256 PKCS8 형식 .p8 — Apple Developer가 발급하는 형태와 동일 */
	private static final String VALID_P8 = """
            -----BEGIN PRIVATE KEY-----
            MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgevZzL1gdAFr88hb2
            OF/2NxApJCzGCEDdfSp6VQO30hyhRANCAAQRWz+jn65BtOMvdyHKcvjBeBSDZH2r
            1RTwjmYSi9R/zpBnuQ4EiMnCqfMPWiZqB4QdbAd0E7oH50VpuZ1P087G
            -----END PRIVATE KEY-----
            """;

	private final RestClient restClient = RestClient.create();

	@Test
	@DisplayName("환경변수가 모두 비어있으면 disabled 모드로 부팅된다")
	void initialize_allBlank_disabledMode() {
		// Given
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, "", "", "");

		// When
		ReflectionTestUtils.invokeMethod(adapter, "initialize");

		// Then
		assertThat(adapter.isEnabled()).isFalse();
	}

	@Test
	@DisplayName("환경변수 일부만 채워져 있으면 IllegalStateException으로 fail-fast")
	void initialize_partialConfig_failsFast() {
		// Given
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, TEAM_ID, "", "");

		// When & Then
		assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(adapter, "initialize"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Apple OAuth 설정 누락");
	}

	@Test
	@DisplayName("잘못된 .p8 형식이면 IllegalStateException으로 fail-fast")
	void initialize_invalidPrivateKey_failsFast() {
		// Given
		String brokenPem = "-----BEGIN PRIVATE KEY-----\nNOT_VALID_BASE64!!!\n-----END PRIVATE KEY-----";
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, TEAM_ID, KEY_ID, brokenPem);

		// When & Then
		assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(adapter, "initialize"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Apple OAuth .p8 private key 파싱 실패");
	}

	@Test
	@DisplayName("정상 .p8 + 모든 환경변수 → enabled 모드로 부팅된다")
	void initialize_validConfig_enabledMode() {
		// Given
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, TEAM_ID, KEY_ID, VALID_P8);

		// When
		ReflectionTestUtils.invokeMethod(adapter, "initialize");

		// Then
		assertThat(adapter.isEnabled()).isTrue();
	}

	@Test
	@DisplayName("환경변수의 \\n 이스케이프된 PEM도 정상 파싱된다")
	void initialize_escapedNewlines_parsesPrivateKey() {
		// Given — 환경변수에 한 줄로 저장된 PEM 시뮬레이션
		String escapedPem = VALID_P8.replace("\n", "\\n");
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, TEAM_ID, KEY_ID, escapedPem);

		// When
		ReflectionTestUtils.invokeMethod(adapter, "initialize");

		// Then
		assertThat(adapter.isEnabled()).isTrue();
	}

	@Test
	@DisplayName("disabled 상태에서 exchangeAuthorizationCode 호출 → APPLE_TOKEN_EXCHANGE_ERROR")
	void exchangeAuthorizationCode_disabled_throwsException() {
		// Given
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, "", "", "");
		ReflectionTestUtils.invokeMethod(adapter, "initialize");

		// When & Then
		assertThatThrownBy(() -> adapter.exchangeAuthorizationCode("any-code"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.APPLE_TOKEN_EXCHANGE_ERROR);
	}

	@Test
	@DisplayName("disabled 상태에서 revokeRefreshToken 호출 → 조용히 무시 (예외 안 던짐)")
	void revokeRefreshToken_disabled_silentlyIgnored() {
		// Given
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, "", "", "");
		ReflectionTestUtils.invokeMethod(adapter, "initialize");

		// When & Then — 예외 던지지 않음
		adapter.revokeRefreshToken("any-token");
	}

	@Test
	@DisplayName("revokeRefreshToken에 null/blank 전달 → 조용히 무시")
	void revokeRefreshToken_blankToken_silentlyIgnored() {
		// Given
		AppleOAuthAdapter adapter = new AppleOAuthAdapter(restClient, CLIENT_ID, TEAM_ID, KEY_ID, VALID_P8);
		ReflectionTestUtils.invokeMethod(adapter, "initialize");

		// When & Then — 예외 던지지 않음
		adapter.revokeRefreshToken(null);
		adapter.revokeRefreshToken("");
		adapter.revokeRefreshToken("   ");
	}
}
