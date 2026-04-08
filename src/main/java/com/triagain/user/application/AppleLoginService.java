package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.AppleLoginUseCase;
import com.triagain.user.port.out.AppleOAuthPort;
import com.triagain.user.port.out.AppleTokenVerifierPort;
import com.triagain.user.port.out.AppleTokenVerifierPort.AppleUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AppleLoginService implements AppleLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(AppleLoginService.class);

    private final AppleTokenVerifierPort appleTokenVerifierPort;
    private final AppleOAuthPort appleOAuthPort;
    private final UserRepositoryPort userRepositoryPort;
    private final JwtProvider jwtProvider;
    private final AppleLoginService self;

    public AppleLoginService(
            AppleTokenVerifierPort appleTokenVerifierPort,
            AppleOAuthPort appleOAuthPort,
            UserRepositoryPort userRepositoryPort,
            JwtProvider jwtProvider,
            @Lazy AppleLoginService self
    ) {
        this.appleTokenVerifierPort = appleTokenVerifierPort;
        this.appleOAuthPort = appleOAuthPort;
        this.userRepositoryPort = userRepositoryPort;
        this.jwtProvider = jwtProvider;
        this.self = self;
    }

    /**
     * Apple 로그인 진입점 — identityToken 검증 → 신규 유저면 즉시 반환,
     * 기존 유저면 트랜잭션 밖에서 authorizationCode backfill 시도(best-effort) 후
     * 트랜잭션 안에서 프로필 동기화 + JWT 발급.
     */
    @Override
    public AppleLoginResult login(AppleLoginCommand command) {
        AppleUserInfo appleUser = appleTokenVerifierPort.verify(command.identityToken());

        // 신규 유저는 backfill 대상 아님 — 회원가입 시점에 처리
        if (userRepositoryPort.findById(appleUser.sub()).isEmpty()) {
            return AppleLoginResult.newUser(appleUser.sub(), appleUser.email());
        }

        // 기존 유저: backfill 시도 (트랜잭션 밖, best-effort)
        String backfilledRefreshToken = tryBackfillRefreshToken(command.authorizationCode());

        return self.completeLogin(appleUser, backfilledRefreshToken);
    }

    /** Apple OAuth refresh_token backfill — 실패해도 로그인은 정상 진행 (WARN 로그만) */
    private String tryBackfillRefreshToken(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return null;
        }
        if (!appleOAuthPort.isEnabled()) {
            return null;
        }
        try {
            return appleOAuthPort.exchangeAuthorizationCode(authorizationCode);
        } catch (Exception e) {
            log.warn("Apple refresh_token backfill 실패 (로그인은 계속 진행): {}", e.getMessage());
            return null;
        }
    }

    /** Apple 로그인 트랜잭션 영역 — 프로필 동기화 + refresh_token backfill 저장 + JWT 발급 */
    @Transactional
    public AppleLoginResult completeLogin(AppleUserInfo appleUser, String backfilledRefreshToken) {
        Optional<User> existing = userRepositoryPort.findById(appleUser.sub());
        if (existing.isEmpty()) {
            // 진입 메서드와 트랜잭션 사이에 탈퇴된 경우 — 신규 유저로 처리
            return AppleLoginResult.newUser(appleUser.sub(), appleUser.email());
        }

        User user = existing.get();
        boolean changed = user.syncAppleProfile(appleUser.email());
        if (backfilledRefreshToken != null) {
            user.updateAppleRefreshToken(backfilledRefreshToken);
            changed = true;
        }
        if (changed) {
            user = userRepositoryPort.save(user);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getProvider(), user.getTokenVersion());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getTokenVersion());

        return AppleLoginResult.existingUser(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirationSeconds(),
                new LoginUserInfo(user.getId(), user.getNickname(), user.getProfileImageUrl())
        );
    }
}