package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginCommand;
import com.triagain.user.port.in.KakaoLoginUseCase.KakaoLoginResult;
import com.triagain.user.port.out.KakaoApiPort;
import com.triagain.user.port.out.KakaoApiPort.KakaoUserInfo;
import com.triagain.user.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KakaoLoginServiceTest {

    @InjectMocks
    private KakaoLoginService kakaoLoginService;

    @Mock
    private KakaoApiPort kakaoApiPort;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private JwtProvider jwtProvider;

    private KakaoUserInfo kakaoUserInfo;

    @BeforeEach
    void setUp() {
        kakaoUserInfo = new KakaoUserInfo("12345", "카카오유저", "kakao@test.com", "https://img.kakao.com/profile.jpg");
    }

    @Test
    @DisplayName("신규 유저 — 카카오 로그인 시 유저 생성 후 JWT 발급")
    void login_newUser_createsUserAndReturnsJwt() {
        // Given
        given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);
        given(userRepositoryPort.findById("12345")).willReturn(Optional.empty());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        // When
        KakaoLoginResult result = kakaoLoginService.login(new KakaoLoginCommand("valid-token"));

        // Then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(1800L);
        assertThat(result.user().isNewUser()).isTrue();
        assertThat(result.user().nickname()).isEqualTo("카카오유저");
        verify(userRepositoryPort).save(any(User.class));
    }

    @Test
    @DisplayName("기존 유저 — 카카오 로그인 시 프로필 갱신 후 JWT 발급")
    void login_existingUser_updatesProfileAndReturnsJwt() {
        // Given
        User existingUser = User.of("12345", "KAKAO", "old@test.com", "기존유저", null, java.time.LocalDateTime.now());
        given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);
        given(userRepositoryPort.findById("12345")).willReturn(Optional.of(existingUser));
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        // When
        KakaoLoginResult result = kakaoLoginService.login(new KakaoLoginCommand("valid-token"));

        // Then
        assertThat(result.user().isNewUser()).isFalse();
        assertThat(result.user().nickname()).isEqualTo("카카오유저");
    }

    @Test
    @DisplayName("유효하지 않은 카카오 토큰 — INVALID_KAKAO_TOKEN 예외")
    void login_invalidToken_throwsException() {
        // Given
        given(kakaoApiPort.getUserInfo("invalid-token"))
                .willThrow(new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN));

        // When & Then
        assertThatThrownBy(() -> kakaoLoginService.login(new KakaoLoginCommand("invalid-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN);
    }

    @Test
    @DisplayName("카카오 API 장애 — KAKAO_API_ERROR 예외")
    void login_kakaoApiError_throwsException() {
        // Given
        given(kakaoApiPort.getUserInfo("any-token"))
                .willThrow(new BusinessException(ErrorCode.KAKAO_API_ERROR));

        // When & Then
        assertThatThrownBy(() -> kakaoLoginService.login(new KakaoLoginCommand("any-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.KAKAO_API_ERROR);
    }

    @Test
    @DisplayName("카카오 email이 null이어도 정상 로그인")
    void login_nullEmail_succeeds() {
        // Given
        KakaoUserInfo noEmailUser = new KakaoUserInfo("12345", "카카오유저", null, null);
        given(kakaoApiPort.getUserInfo("valid-token")).willReturn(noEmailUser);
        given(userRepositoryPort.findById("12345")).willReturn(Optional.empty());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        // When
        KakaoLoginResult result = kakaoLoginService.login(new KakaoLoginCommand("valid-token"));

        // Then
        assertThat(result.user().isNewUser()).isTrue();
    }
}