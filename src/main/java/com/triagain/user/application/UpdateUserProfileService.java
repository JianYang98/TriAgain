package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.UpdateUserProfileUseCase;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserProfileService implements UpdateUserProfileUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    @Transactional
    public UpdateProfileResult updateProfile(UpdateProfileCommand command) {
        User user = userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateProfile(command.nickname(), command.profileImageUrl());
        User saved = userRepositoryPort.save(user);

        return new UpdateProfileResult(
                saved.getId(),
                saved.getEmail(),
                saved.getNickname(),
                saved.getProfileImageUrl(),
                saved.getCreatedAt()
        );
    }
}
