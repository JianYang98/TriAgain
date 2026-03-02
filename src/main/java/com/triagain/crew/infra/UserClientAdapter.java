package com.triagain.crew.infra;

import com.triagain.crew.port.out.UserPort;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserClientAdapter implements UserPort {

    private final UserRepositoryPort userRepositoryPort;

    /** 유저 ID 목록으로 프로필 정보 일괄 조회 */
    @Override
    public Map<String, UserProfile> findProfilesByIds(List<String> userIds) {
        return userRepositoryPort.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> new UserProfile(user.getNickname(), user.getProfileImageUrl())
                ));
    }
}
