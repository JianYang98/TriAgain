package com.triagain.user.port.in;

public interface AppleSignupUseCase {

    /** Apple 회원가입 — Apple 인증 + 약관 동의 + 닉네임으로 신규 유저 생성 */
    AppleSignupResult signup(AppleSignupCommand command);

    record AppleSignupCommand(String identityToken, String appleId, String nickname, boolean termsAgreed) {
    }

    record AppleSignupResult(String accessToken, String refreshToken, long accessTokenExpiresIn,
                             AppleSignupUserInfo user) {
    }

    record AppleSignupUserInfo(String id, String nickname, String profileImageUrl) {
    }
}
