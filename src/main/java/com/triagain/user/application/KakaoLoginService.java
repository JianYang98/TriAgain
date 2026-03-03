package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.KakaoLoginUseCase;
import com.triagain.user.port.out.KakaoApiPort;
import com.triagain.user.port.out.KakaoApiPort.KakaoUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KakaoLoginService implements KakaoLoginUseCase {

    private final KakaoApiPort kakaoApiPort;
    private final UserRepositoryPort userRepositoryPort;
    private final JwtProvider jwtProvider;

    /** 카카오 로그인 — 기존 유저면 JWT 발급, 신규 유저면 카카오 프로필만 반환 */
    @Override
    @Transactional
    public KakaoLoginResult login(KakaoLoginCommand command) {
        KakaoUserInfo kakaoUser = kakaoApiPort.getUserInfo(command.kakaoAccessToken());

        Optional<User> existing = userRepositoryPort.findById(kakaoUser.id());

        if (existing.isEmpty()) {
            return KakaoLoginResult.newUser(
                    kakaoUser.id(),
                    new KakaoProfile(kakaoUser.nickname(), kakaoUser.email(), kakaoUser.profileImageUrl())
            );
        }

        User user = existing.get();
        user.updateKakaoProfile(kakaoUser.nickname(), kakaoUser.email(), kakaoUser.profileImageUrl());
        User saved = userRepositoryPort.save(user);

        String accessToken = jwtProvider.createAccessToken(saved.getId(), saved.getProvider());
        String refreshToken = jwtProvider.createRefreshToken(saved.getId());

        return KakaoLoginResult.existingUser(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirationSeconds(),
                new LoginUserInfo(saved.getId(), saved.getNickname(), saved.getProfileImageUrl())
        );
    }
}
