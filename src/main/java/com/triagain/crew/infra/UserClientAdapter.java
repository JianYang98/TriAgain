package com.triagain.crew.infra;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.triagain.crew.port.out.UserPort;
import com.triagain.user.port.in.UserProfileQueryUseCase;
import com.triagain.user.port.in.UserProfileQueryUseCase.UserProfileDto;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserClientAdapter implements UserPort {

	private final UserProfileQueryUseCase userProfileQueryUseCase;

	/** 유저 ID 목록으로 프로필 정보 일괄 조회 */
	@Override
	public Map<String, UserProfile> findProfilesByIds(List<String> userIds) {
		return userProfileQueryUseCase.findProfilesByIds(userIds).entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> new UserProfile(e.getValue().nickname(), e.getValue().profileImageUrl())
				));
	}
}
