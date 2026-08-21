package com.triagain.user.application;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.UpdateUserProfileUseCase;
import com.triagain.user.port.out.UserRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UpdateUserProfileService implements UpdateUserProfileUseCase {

	private final UserRepositoryPort userRepositoryPort;
	private final StoragePort storagePort;

	@Override
	@Transactional
	public UpdateProfileResult updateProfile(UpdateProfileCommand command) {
		User user = userRepositoryPort.findById(command.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// updateProfile() 안에서 또 불리지만 중복이 아니다 — 그 안의 !isBlank() 가드는
		// 공백만으로 된 닉네임(Character.isWhitespace 기준, 전각공백 U+3000 등)에서 검증 블록을
		// 통째로 건너뛴다. 호출 시점을 앞당겨 가입 경로와 동일하게 U004를 낸다.
		User.validateNickname(command.nickname());
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

	/** 이미지 URL 검증 — S3 버킷 도메인 + profiles/{userId}/ + UUID + 확장자 정규식 확인 */
	private void validateImageUrl(String imageUrl, String userId) {
		String bucketDomain = storagePort.getBucketDomain();
		String expectedPrefix = bucketDomain + StoragePort.PROFILE_PREFIX + "/" + userId + "/";
		String regex = "^" + Pattern.quote(expectedPrefix) + "[a-f0-9\\-]+\\.(jpg|png|webp)$";
		if (!imageUrl.matches(regex)) {
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
