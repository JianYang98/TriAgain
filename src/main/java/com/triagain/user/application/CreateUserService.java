package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.CreateUserUseCase;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateUserService implements CreateUserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    @Transactional
    public CreateUserResult createUser(CreateUserCommand command) {
        if (userRepositoryPort.existsByEmail(command.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(command.email(), command.nickname());
        User saved = userRepositoryPort.save(user);

        return new CreateUserResult(
                saved.getId(),
                saved.getEmail(),
                saved.getNickname(),
                saved.getCreatedAt()
        );
    }
}
