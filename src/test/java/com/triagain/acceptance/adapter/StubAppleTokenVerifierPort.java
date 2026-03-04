package com.triagain.acceptance.adapter;

import com.triagain.user.port.out.AppleTokenVerifierPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Apple 토큰 검증 스텁 — 테스트에서 실제 Apple JWKS 호출 없이 고정 사용자 정보 반환 */
@Component
@Primary
public class StubAppleTokenVerifierPort implements AppleTokenVerifierPort {

    private static final String STUB_SUB = "001234.abcdef1234.5678";
    private static final String STUB_EMAIL = "apple@privaterelay.appleid.com";

    @Override
    public AppleUserInfo verify(String identityToken) {
        return new AppleUserInfo(STUB_SUB, STUB_EMAIL);
    }

    public String getStubSub() {
        return STUB_SUB;
    }

    public String getStubEmail() {
        return STUB_EMAIL;
    }
}
