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

    /** 카카오 로그인 — 신규 유저 생성 또는 기존 유저 프로필 갱신 후 JWT 발급 */
    @Override
    @Transactional
    public KakaoLoginResult login(KakaoLoginCommand command) {
        KakaoUserInfo kakaoUser = kakaoApiPort.getUserInfo(command.kakaoAccessToken());

        Optional<User> existing = userRepositoryPort.findById(kakaoUser.id());
        boolean isNewUser;
        User user;

        if (existing.isEmpty()) {
            user = User.createFromKakao(
                    kakaoUser.id(), kakaoUser.nickname(),
                    kakaoUser.email(), kakaoUser.profileImageUrl()
            );
            isNewUser = true;
        } else {
            user = existing.get();
            user.updateKakaoProfile(kakaoUser.nickname(), kakaoUser.email(), kakaoUser.profileImageUrl());
            isNewUser = false;
        }

        User saved = userRepositoryPort.save(user);

        String accessToken = jwtProvider.createAccessToken(saved.getId(), saved.getProvider());
        String refreshToken = jwtProvider.createRefreshToken(saved.getId());

        return new KakaoLoginResult(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirationSeconds(),
                new LoginUserInfo(saved.getId(), saved.getNickname(), saved.getProfileImageUrl(), isNewUser)
        );
    }
}
