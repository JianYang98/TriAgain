package com.triagain.user.port.in;

import java.time.LocalDateTime;

public interface UpdateUserProfileUseCase {

    UpdateProfileResult updateProfile(UpdateProfileCommand command);

    record UpdateProfileCommand(String userId, String nickname, String profileImageUrl) {
    }

    record UpdateProfileResult(String id, String email, String nickname, String profileImageUrl, LocalDateTime createdAt) {
    }
}
