package com.triagain.user.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class User {

    private final String id;
    private final String email;
    private String nickname;
    private String profileImageUrl;
    private final LocalDateTime createdAt;

    private User(String id, String email, String nickname, String profileImageUrl, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
    }

    public static User create(String email, String nickname) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }
        return new User(
                UUID.randomUUID().toString(),
                email,
                nickname,
                null,
                LocalDateTime.now()
        );
    }

    public static User of(String id, String email, String nickname, String profileImageUrl, LocalDateTime createdAt) {
        return new User(id, email, nickname, profileImageUrl, createdAt);
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public String getId() {
        return id;
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
