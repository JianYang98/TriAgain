package com.triagain.user.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;

import java.time.LocalDateTime;

public class User {

    private final String id;
    private final String provider;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private final LocalDateTime createdAt;

    private User(String id, String provider, String email, String nickname, String profileImageUrl, LocalDateTime createdAt) {
        this.id = id;
        this.provider = provider;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
    }

    /** 카카오 로그인으로 신규 유저 생성 — kakaoId를 PK로 사용 */
    public static User createFromKakao(String kakaoId, String nickname, String email, String profileImageUrl) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ErrorCode.NICKNAME_REQUIRED);
        }
        return new User(
                kakaoId,
                "KAKAO",
                email,
                nickname,
                profileImageUrl,
                LocalDateTime.now()
        );
    }

    /** DB 조회 결과 → 도메인 객체 복원 */
    public static User of(String id, String provider, String email, String nickname, String profileImageUrl, LocalDateTime createdAt) {
        return new User(id, provider, email, nickname, profileImageUrl, createdAt);
    }

    /** 프로필 수정 — 닉네임/프로필 이미지 변경 */
    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    /** 카카오 재로그인 시 프로필 갱신 */
    public void updateKakaoProfile(String nickname, String email, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        this.email = email;
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public String getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
