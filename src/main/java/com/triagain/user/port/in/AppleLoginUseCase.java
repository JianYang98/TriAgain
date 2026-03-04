package com.triagain.user.port.in;

public interface AppleLoginUseCase {

    /** Apple 로그인 — 기존 유저면 JWT 발급, 신규 유저면 회원가입 필요 응답 */
    AppleLoginResult login(AppleLoginCommand command);

    record AppleLoginCommand(String identityToken) {
    }

    /** 로그인 결과 — isNewUser로 분기 */
    record AppleLoginResult(
            boolean isNewUser,
            // 기존 유저 (isNewUser=false)
            String accessToken,
            String refreshToken,
            Long accessTokenExpiresIn,
            LoginUserInfo user,
            // 신규 유저 (isNewUser=true)
            String appleId,
            String email
    ) {
        /** 기존 유저 로그인 성공 */
        public static AppleLoginResult existingUser(String accessToken, String refreshToken,
                                                     long accessTokenExpiresIn, LoginUserInfo user) {
            return new AppleLoginResult(false, accessToken, refreshToken, accessTokenExpiresIn, user, null, null);
        }

        /** 신규 유저 — 회원가입 필요 */
        public static AppleLoginResult newUser(String appleId, String email) {
            return new AppleLoginResult(true, null, null, null, null, appleId, email);
        }
    }

    record LoginUserInfo(String id, String nickname, String profileImageUrl) {
    }
}
