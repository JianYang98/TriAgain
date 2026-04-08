package com.triagain.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User createActiveUser() {
        return User.of("user-1", "KAKAO", "test@test.com", "테스트", null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null, 0);
    }

    @DisplayName("withdraw() 호출하면 닉네임이 '탈퇴한 사용자'로 변경되고 email/profileImageUrl/fcmToken이 null이 된다")
    @Test
    void withdraw_clearsPersonalInfo() {
        // Given
        User user = User.of("user-1", "KAKAO", "test@test.com", "테스트",
                "https://img.com/profile.jpg", "fcm-token-123", null,
                LocalDateTime.now(), LocalDateTime.now(), null, 0);

        // When
        user.withdraw();

        // Then
        assertThat(user.getNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.getFcmToken()).isNull();
    }

    @DisplayName("withdraw() 호출하면 deletedAt이 설정되고 tokenVersion이 증가한다")
    @Test
    void withdraw_setsDeletedAtAndIncrementsTokenVersion() {
        // Given
        User user = createActiveUser();
        int originalTokenVersion = user.getTokenVersion();

        // When
        user.withdraw();

        // Then
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getTokenVersion()).isEqualTo(originalTokenVersion + 1);
    }

    @DisplayName("isWithdrawn()은 deletedAt이 null이 아니면 true를 반환한다")
    @Test
    void isWithdrawn_returnsTrueWhenDeletedAtIsNotNull() {
        // Given
        User activeUser = createActiveUser();
        User withdrawnUser = User.of("user-2", "KAKAO", null, "탈퇴한 사용자",
                null, null, null, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), 1);

        // When & Then
        assertThat(activeUser.isWithdrawn()).isFalse();
        assertThat(withdrawnUser.isWithdrawn()).isTrue();
    }

    @DisplayName("reactivate() 호출하면 deletedAt이 null이 되고 tokenVersion이 증가한다")
    @Test
    void reactivate_clearsDeletedAtAndIncrementsTokenVersion() {
        // Given
        User user = User.of("user-1", "KAKAO", null, "탈퇴한 사용자",
                null, null, null, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), 1);
        int originalTokenVersion = user.getTokenVersion();

        // When
        user.reactivate("새닉네임", "new@test.com", "https://img.com/new.jpg", null);

        // Then
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(originalTokenVersion + 1);
        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getEmail()).isEqualTo("new@test.com");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://img.com/new.jpg");
    }
}