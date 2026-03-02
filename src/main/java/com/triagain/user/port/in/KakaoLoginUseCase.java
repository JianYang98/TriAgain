package com.triagain.user.port.in;

public interface KakaoLoginUseCase {

    /** 카카오 로그인 — 카카오 Access Token으로 인증 후 자체 JWT 발급 */
    KakaoLoginResult login(KakaoLoginCommand command);

    record KakaoLoginCommand(String kakaoAccessToken) {
    }

    record KakaoLoginResult(String accessToken, String refreshToken, long accessTokenExpiresIn, LoginUserInfo user) {
    }

    record LoginUserInfo(String id, String nickname, String profileImageUrl, boolean isNewUser) {
    }
}
