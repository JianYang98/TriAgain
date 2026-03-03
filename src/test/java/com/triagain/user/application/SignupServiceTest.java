package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.SignupUseCase.SignupCommand;
import com.triagain.user.port.in.SignupUseCase.SignupResult;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @InjectMocks
    private SignupService signupService;

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
    @DisplayName("정상 회원가입 — 유저 생성 + JWT 발급")
    void signup_success() {
        // Given
        given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);
        given(userRepositoryPort.findById("12345")).willReturn(Optional.empty());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        SignupCommand command = new SignupCommand("valid-token", "12345", "내닉네임", true);

        // When
        SignupResult result = signupService.signup(command);

        // Then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(1800L);
        assertThat(result.user().id()).isEqualTo("12345");
        assertThat(result.user().nickname()).isEqualTo("내닉네임");
        verify(userRepositoryPort).save(any(User.class));
    }

    @Test
    @DisplayName("약관 미동의 — TERMS_NOT_AGREED 에러")
    void signup_termsNotAgreed_throwsException() {
        // Given
        SignupCommand command = new SignupCommand("valid-token", "12345", "내닉네임", false);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TERMS_NOT_AGREED);

        verify(kakaoApiPort, never()).getUserInfo(anyString());
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("이미 가입된 유저 — USER_ALREADY_EXISTS 에러 (409)")
    void signup_alreadyExists_throwsException() {
        // Given
        given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);
        given(userRepositoryPort.findById("12345")).willReturn(
                Optional.of(User.of("12345", "KAKAO", "kakao@test.com", "기존유저", null,
                        java.time.LocalDateTime.now(), java.time.LocalDateTime.now()))
        );

        SignupCommand command = new SignupCommand("valid-token", "12345", "내닉네임", true);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_ALREADY_EXISTS);

        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("잘못된 카카오 토큰 — INVALID_KAKAO_TOKEN 에러")
    void signup_invalidToken_throwsException() {
        // Given
        given(kakaoApiPort.getUserInfo("invalid-token"))
                .willThrow(new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN));

        SignupCommand command = new SignupCommand("invalid-token", "12345", "내닉네임", true);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_KAKAO_TOKEN);
    }

    @Test
    @DisplayName("kakaoId 불일치 — KAKAO_ID_MISMATCH 에러")
    void signup_kakaoIdMismatch_throwsException() {
        // Given
        given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);

        SignupCommand command = new SignupCommand("valid-token", "99999", "내닉네임", true);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.KAKAO_ID_MISMATCH);

        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("닉네임 1자 — INVALID_NICKNAME 에러")
    void signup_nicknameTooShort_throwsException() {
        // Given
        SignupCommand command = new SignupCommand("valid-token", "12345", "가", true);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NICKNAME);
    }

    @Test
    @DisplayName("닉네임 13자 — INVALID_NICKNAME 에러")
    void signup_nicknameTooLong_throwsException() {
        // Given
        SignupCommand command = new SignupCommand("valid-token", "12345", "가나다라마바사아자차카타파", true);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NICKNAME);
    }

    @Test
    @DisplayName("닉네임 특수문자 포함 — INVALID_NICKNAME 에러")
    void signup_nicknameWithSpecialChars_throwsException() {
        // Given
        SignupCommand command = new SignupCommand("valid-token", "12345", "닉네임!@#", true);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NICKNAME);
    }

    @Test
    @DisplayName("닉네임 빈값 — NICKNAME_REQUIRED 에러")
    void signup_nicknameBlank_throwsException() {
        // Given
        SignupCommand command = new SignupCommand("valid-token", "12345", "   ", true);

        // When & Then
        assertThatThrownBy(() -> signupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_REQUIRED);
    }

    @Test
    @DisplayName("닉네임 앞뒤 공백 트림 후 정상 가입")
    void signup_nicknameWithSpaces_trimsAndSucceeds() {
        // Given
        given(kakaoApiPort.getUserInfo("valid-token")).willReturn(kakaoUserInfo);
        given(userRepositoryPort.findById("12345")).willReturn(Optional.empty());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        SignupCommand command = new SignupCommand("valid-token", "12345", "  내닉네임  ", true);

        // When
        SignupResult result = signupService.signup(command);

        // Then
        assertThat(result.user().nickname()).isEqualTo("내닉네임");
    }
}
