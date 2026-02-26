package com.triagain.user.port.in;

import java.time.LocalDateTime;

public interface GetUserUseCase {

    UserResult getUser(String userId);

    record UserResult(String id, String email, String nickname, String profileImageUrl, LocalDateTime createdAt) {
    }
}
