package com.triagain.user.port.in;

public interface SignupUseCase {

    /** 회원가입 — 카카오 인증 + 약관 동의 + 닉네임으로 신규 유저 생성 */
    SignupResult signup(SignupCommand command);

    record SignupCommand(String kakaoAccessToken, String kakaoId, String nickname, boolean termsAgreed) {
    }

    record SignupResult(String accessToken, String refreshToken, long accessTokenExpiresIn, SignupUserInfo user) {
    }

    record SignupUserInfo(String id, String nickname, String profileImageUrl) {
    }
}
