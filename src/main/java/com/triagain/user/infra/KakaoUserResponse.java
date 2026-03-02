package com.triagain.user.infra;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 카카오 API /v2/user/me 응답 역직렬화 */
public record KakaoUserResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount,
        Properties properties
) {

    public record KakaoAccount(
            String email,
            Profile profile
    ) {

        public record Profile(
                String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl
        ) {
        }
    }

    public record Properties(
            String nickname
    ) {
    }

    /** 닉네임 — kakao_account.profile > properties 순 fallback */
    public String extractNickname() {
        if (kakaoAccount != null && kakaoAccount.profile() != null && kakaoAccount.profile().nickname() != null) {
            return kakaoAccount.profile().nickname();
        }
        if (properties != null && properties.nickname() != null) {
            return properties.nickname();
        }
        return "카카오유저";
    }

    /** 이메일 — kakao_account.email, 서비스 약관 미동의 시 null */
    public String extractEmail() {
        return kakaoAccount != null ? kakaoAccount.email() : null;
    }

    /** 프로필 이미지 URL — kakao_account.profile, 미설정 시 null */
    public String extractProfileImageUrl() {
        if (kakaoAccount != null && kakaoAccount.profile() != null) {
            return kakaoAccount.profile().profileImageUrl();
        }
        return null;
    }
}
