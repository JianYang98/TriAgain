package com.triagain.user.port.out;

public interface AppleOAuthPort {

    /** authorizationCode를 Apple /auth/token과 교환하여 refresh_token 발급 — Apple 회원가입/로그인 시 사용 */
    String exchangeAuthorizationCode(String authorizationCode);

    /** Apple refresh_token 무효화 — 회원탈퇴 시 호출. App Store 5.1.1(v) 요건 */
    void revokeRefreshToken(String refreshToken);

    /** Apple OAuth 활성 여부 — 환경변수 미설정 시 false (회원가입/탈퇴 시 graceful 처리용) */
    boolean isEnabled();
}