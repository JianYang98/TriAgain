package com.triagain.user.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.UpdateUserProfileUseCase;
import com.triagain.user.port.out.UserRepositoryPort;
import com.triagain.verification.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserProfileService implements UpdateUserProfileUseCase {

    private static final String PROFILE_PREFIX = "profiles";

    private final UserRepositoryPort userRepositoryPort;
    private final StoragePort storagePort;

    @Override
    @Transactional
    public UpdateProfileResult updateProfile(UpdateProfileCommand command) {
        User user = userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateProfile(command.nickname(), command.profileImageUrl());
        User saved = userRepositoryPort.save(user);

        return toResult(saved);
    }

    /** 프로필 이미지 변경 — null이면 기본 이미지로 리셋, 값이면 S3 경로 검증 후 업데이트 */
    @Override
    @Transactional
    public UpdateProfileResult updateProfileImage(String userId, String imageUrl) {
        if (imageUrl != null) {
            validateImageUrl(imageUrl, userId);
        }

        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateProfileImage(imageUrl);
        User saved = userRepositoryPort.save(user);

        return toResult(saved);
    }

    /** 이미지 URL 검증 — S3 버킷 도메인 + profiles/{userId}/ 경로 확인 */
    private void validateImageUrl(String imageUrl, String userId) {
        String bucketDomain = storagePort.getBucketDomain();
        String expectedPrefix = bucketDomain + PROFILE_PREFIX + "/" + userId + "/";
        if (!imageUrl.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
        }
    }

    private UpdateProfileResult toResult(User user) {
        return new UpdateProfileResult(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getCreatedAt()
        );
    }
}
