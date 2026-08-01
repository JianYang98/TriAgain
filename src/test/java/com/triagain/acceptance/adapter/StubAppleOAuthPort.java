package com.triagain.acceptance.adapter;

import com.triagain.user.port.out.AppleOAuthPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Apple OAuth 스텁 — Cucumber/E2E 테스트에서 실제 Apple /auth/token, /auth/revoke 호출 없이
 * 고정된 refresh_token 반환. 실제 AppleOAuthAdapter는 환경변수 미설정 시 disabled 모드로
 * 동작하지만, 회원가입은 차단되므로 cucumber 시나리오에서 enabled 동작이 필요할 때 사용한다.
 */
@Component
@Primary
public class StubAppleOAuthPort implements AppleOAuthPort {

	public static final String STUB_REFRESH_TOKEN = "stub-apple-refresh-token";

	@Override
	public String exchangeAuthorizationCode(String authorizationCode) {
		return STUB_REFRESH_TOKEN;
	}

	@Override
	public void revokeRefreshToken(String refreshToken) {
		// no-op
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}