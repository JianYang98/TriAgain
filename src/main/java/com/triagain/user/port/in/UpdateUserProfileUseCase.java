package com.triagain.user.port.in;

import java.time.LocalDateTime;

public interface UpdateUserProfileUseCase {

	UpdateProfileResult updateProfile(UpdateProfileCommand command);

	/** 프로필 이미지 변경 — null이면 기본 이미지로 리셋 */
	UpdateProfileResult updateProfileImage(String userId, String imageUrl);

	record UpdateProfileCommand(String userId, String nickname, String profileImageUrl) {
	}

	record UpdateProfileResult(String id, String email, String nickname, String profileImageUrl, LocalDateTime createdAt) {
	}
}
