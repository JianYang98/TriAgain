package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.AppleLoginUseCase;
import com.triagain.user.port.out.AppleTokenVerifierPort;
import com.triagain.user.port.out.AppleTokenVerifierPort.AppleUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppleLoginService implements AppleLoginUseCase {

    private final AppleTokenVerifierPort appleTokenVerifierPort;
    private final UserRepositoryPort userRepositoryPort;
    private final JwtProvider jwtProvider;

    /** Apple 로그인 — 기존 유저면 JWT 발급, 신규 유저면 appleId + email만 반환 */
    @Override
    @Transactional
    public AppleLoginResult login(AppleLoginCommand command) {
        AppleUserInfo appleUser = appleTokenVerifierPort.verify(command.identityToken());

        Optional<User> existing = userRepositoryPort.findById(appleUser.sub());

        if (existing.isEmpty()) {
            return AppleLoginResult.newUser(appleUser.sub(), appleUser.email());
        }

        User user = existing.get();
        boolean profileChanged = user.syncAppleProfile(appleUser.email());
        if (profileChanged) {
            user = userRepositoryPort.save(user);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getProvider());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        return AppleLoginResult.existingUser(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirationSeconds(),
                new LoginUserInfo(user.getId(), user.getNickname(), user.getProfileImageUrl())
        );
    }
}
