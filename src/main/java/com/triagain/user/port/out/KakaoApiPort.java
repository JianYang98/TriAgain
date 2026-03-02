package com.triagain.user.port.out;

public interface KakaoApiPort {

    /** 카카오 Access Token으로 사용자 정보 조회 */
    KakaoUserInfo getUserInfo(String kakaoAccessToken);

    record KakaoUserInfo(String id, String nickname, String email, String profileImageUrl) {
    }
}
