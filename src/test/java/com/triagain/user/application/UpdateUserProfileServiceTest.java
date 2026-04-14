package com.triagain.user.application;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileServiceTest {

    @InjectMocks
    private UpdateUserProfileService updateUserProfileService;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private StoragePort storagePort;

    @Test
    @DisplayName("프로필 이미지 변경 — URL 전달 시 S3 경로 검증 후 이미지 업데이트")
    void updateProfileImage_withUrl_updatesImage() {
        // Given
        given(storagePort.getBucketDomain()).willReturn("https://s3.com/");
        User user = User.of("user-1", "KAKAO", "test@test.com", "테스트", null,
                null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0);
        given(userRepositoryPort.findById("user-1")).willReturn(Optional.of(user));
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        var result = updateUserProfileService.updateProfileImage(
                "user-1", "https://s3.com/profiles/user-1/550e8400-e29b-41d4-a716-446655440000.jpg");

        // Then
        assertThat(result.profileImageUrl()).isEqualTo(
                "https://s3.com/profiles/user-1/550e8400-e29b-41d4-a716-446655440000.jpg");
    }

    @Test
    @DisplayName("프로필 이미지 변경 — null 전달 시 기본 이미지로 리셋 (URL 검증 스킵)")
    void updateProfileImage_withNull_resetsToDefault() {
        // Given
        User user = User.of("user-1", "KAKAO", "test@test.com", "테스트",
                "https://s3.com/profiles/user-1/old.jpg",
                null, null, LocalDateTime.now(), LocalDateTime.now(), null, 0);
        given(userRepositoryPort.findById("user-1")).willReturn(Optional.of(user));
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        var result = updateUserProfileService.updateProfileImage("user-1", null);

        // Then
        assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("프로필 이미지 변경 — 존재하지 않는 유저 시 USER_NOT_FOUND 예외")
    void updateProfileImage_userNotFound_throwsException() {
        // Given
        given(storagePort.getBucketDomain()).willReturn("https://s3.com/");
        given(userRepositoryPort.findById("nonexistent")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> updateUserProfileService.updateProfileImage(
                "nonexistent", "https://s3.com/profiles/nonexistent/550e8400-e29b-41d4-a716-446655440000.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("프로필 이미지 변경 — 외부 도메인 URL 시 INVALID_IMAGE_URL 예외")
    void updateProfileImage_externalUrl_throwsInvalidImageUrl() {
        // Given
        given(storagePort.getBucketDomain()).willReturn("https://s3.com/");

        // When & Then
        assertThatThrownBy(() -> updateUserProfileService.updateProfileImage(
                "user-1", "https://evil.com/profiles/user-1/550e8400-e29b-41d4-a716-446655440000.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE_URL);
    }

    @Test
    @DisplayName("프로필 이미지 변경 — 타인의 S3 경로 시 INVALID_IMAGE_URL 예외")
    void updateProfileImage_otherUsersPath_throwsException() {
        // Given
        given(storagePort.getBucketDomain()).willReturn("https://s3.com/");

        // When & Then
        assertThatThrownBy(() -> updateUserProfileService.updateProfileImage(
                "user-1", "https://s3.com/profiles/other-user/550e8400-e29b-41d4-a716-446655440000.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE_URL);
    }
}
