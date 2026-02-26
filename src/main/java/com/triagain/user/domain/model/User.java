package com.triagain.user.domain.model;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;

import com.triagain.common.util.IdGenerator;

import java.time.LocalDateTime;

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
            throw new BusinessException(ErrorCode.EMAIL_REQUIRED);
        }
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ErrorCode.NICKNAME_REQUIRED);
        }
        return new User(
                IdGenerator.generate("USR"),
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
