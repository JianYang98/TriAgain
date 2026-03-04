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

        // KAKAO 사용자 정보 조회
        KakaoUserInfo kakaoUser = kakaoApiPort.getUserInfo(command.kakaoAccessToken());

        // 서비스 사용자 조회
        Optional<User> existing = userRepositoryPort.findById(kakaoUser.id());

        // 신규 유저 라면 토큰 발급 x
        if (existing.isEmpty()) {
            return KakaoLoginResult.newUser(
                    kakaoUser.id(),
                    new KakaoProfile(kakaoUser.nickname(), kakaoUser.email(), kakaoUser.profileImageUrl())
            );
        }

        // 기존 유저라면 액세스토큰/리프레쉬 토큰 발급
        User user = existing.get();
        boolean profileChanged = user.syncKakaoProfile(kakaoUser.email(), kakaoUser.profileImageUrl());
        if (profileChanged) { // 프로필 변경시 체인지! 프로필 이미지!
            user = userRepositoryPort.save(user);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getProvider());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        return KakaoLoginResult.existingUser(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirationSeconds(),
                new LoginUserInfo(user.getId(), user.getNickname(), user.getProfileImageUrl())
        );
    }
}
