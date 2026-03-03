package com.triagain.user.port.in;

public interface KakaoLoginUseCase {

    /** 카카오 로그인 — 기존 유저면 JWT 발급, 신규 유저면 회원가입 필요 응답 */
    KakaoLoginResult login(KakaoLoginCommand command);

    record KakaoLoginCommand(String kakaoAccessToken) {
    }

    /** 로그인 결과 — isNewUser로 분기 */
    record KakaoLoginResult(
            boolean isNewUser,
            // 기존 유저 (isNewUser=false)
            String accessToken,
            String refreshToken,
            Long accessTokenExpiresIn,
            LoginUserInfo user,
            // 신규 유저 (isNewUser=true)
            String kakaoId,
            KakaoProfile kakaoProfile
    ) {
        /** 기존 유저 로그인 성공 */
        public static KakaoLoginResult existingUser(String accessToken, String refreshToken,
                                                     long accessTokenExpiresIn, LoginUserInfo user) {
            return new KakaoLoginResult(false, accessToken, refreshToken, accessTokenExpiresIn, user, null, null);
        }

        /** 신규 유저 — 회원가입 필요 */
        public static KakaoLoginResult newUser(String kakaoId, KakaoProfile kakaoProfile) {
            return new KakaoLoginResult(true, null, null, null, null, kakaoId, kakaoProfile);
        }
    }

    record LoginUserInfo(String id, String nickname, String profileImageUrl) {
    }

    record KakaoProfile(String nickname, String email, String profileImageUrl) {
    }
}
