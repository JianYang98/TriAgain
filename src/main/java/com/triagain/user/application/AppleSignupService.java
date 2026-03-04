package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.AppleSignupUseCase;
import com.triagain.user.port.out.AppleTokenVerifierPort;
import com.triagain.user.port.out.AppleTokenVerifierPort.AppleUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleSignupService implements AppleSignupUseCase {

    private final AppleTokenVerifierPort appleTokenVerifierPort;
    private final UserRepositoryPort userRepositoryPort;
    private final JwtProvider jwtProvider;

    /** Apple 회원가입 — 닉네임 검증 → 약관 검증 → Apple 본인 확인 → 중복 확인 → 유저 생성 */
    @Override
    @Transactional
    public AppleSignupResult signup(AppleSignupCommand command) {
        String trimmedNickname = command.nickname() != null ? command.nickname().trim() : null;
        User.validateNickname(trimmedNickname);

        if (!command.termsAgreed()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }

        AppleUserInfo appleUser = appleTokenVerifierPort.verify(command.identityToken());

        if (!appleUser.sub().equals(command.appleId())) {
            throw new BusinessException(ErrorCode.APPLE_ID_MISMATCH);
        }

        if (userRepositoryPort.findById(appleUser.sub()).isPresent()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = User.createFromApple(appleUser.sub(), trimmedNickname, appleUser.email());
        User saved = userRepositoryPort.save(user);

        String accessToken = jwtProvider.createAccessToken(saved.getId(), saved.getProvider());
        String refreshToken = jwtProvider.createRefreshToken(saved.getId());

        return new AppleSignupResult(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirationSeconds(),
                new AppleSignupUserInfo(saved.getId(), saved.getNickname(), saved.getProfileImageUrl())
        );
    }
}
