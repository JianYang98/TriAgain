package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.SignupUseCase;
import com.triagain.user.port.out.KakaoApiPort;
import com.triagain.user.port.out.KakaoApiPort.KakaoUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService implements SignupUseCase {

    private final KakaoApiPort kakaoApiPort;
    private final UserRepositoryPort userRepositoryPort;
    private final JwtProvider jwtProvider;

    /** 회원가입 — 닉네임 검증 → 약관 검증 → 카카오 본인 확인 → 중복 확인 → 유저 생성 */
    @Override
    @Transactional
    public SignupResult signup(SignupCommand command) {
        String trimmedNickname = command.nickname() != null ? command.nickname().trim() : null;
        User.validateNickname(trimmedNickname);

        if (!command.termsAgreed()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }

        KakaoUserInfo kakaoUser = kakaoApiPort.getUserInfo(command.kakaoAccessToken());

        // 카카오 토큰의 실제 주인과 요청한 kakaoId가 일치하는지 검증 — 타인 명의 계정 생성 방지
        if (!kakaoUser.id().equals(command.kakaoId())) {
            throw new BusinessException(ErrorCode.KAKAO_ID_MISMATCH);
        }


        if (userRepositoryPort.findById(kakaoUser.id()).isPresent()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = User.createFromKakao(
                kakaoUser.id(), trimmedNickname, kakaoUser.email(), kakaoUser.profileImageUrl()
        );
        User saved = userRepositoryPort.save(user);

        String accessToken = jwtProvider.createAccessToken(saved.getId(), saved.getProvider());
        String refreshToken = jwtProvider.createRefreshToken(saved.getId());

        // 회원가입된 유저 + 생성된 토큰  리턴
        return new SignupResult(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirationSeconds(),
                new SignupUserInfo(saved.getId(), saved.getNickname(), saved.getProfileImageUrl())
        );
    }
}
