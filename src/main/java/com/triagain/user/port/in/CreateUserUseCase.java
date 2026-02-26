package com.triagain.user.port.in;

import java.time.LocalDateTime;

public interface CreateUserUseCase {

    CreateUserResult createUser(CreateUserCommand command);

    record CreateUserCommand(String email, String nickname) {
    }

    record CreateUserResult(String id, String email, String nickname, LocalDateTime createdAt) {
    }
}
