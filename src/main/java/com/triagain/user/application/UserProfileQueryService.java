package com.triagain.user.application;

import com.triagain.user.port.in.UserProfileQueryUseCase;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 타 Context 전용 유저 프로필 조회 서비스 */
@Service
@RequiredArgsConstructor
public class UserProfileQueryService implements UserProfileQueryUseCase {

    private final UserRepositoryPort userRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public Map<String, UserProfileDto> findProfilesByIds(List<String> userIds) {
        return userRepositoryPort.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(
                        user -> user.getId(),
                        user -> new UserProfileDto(user.getNickname(), user.getProfileImageUrl())
                ));
    }
}