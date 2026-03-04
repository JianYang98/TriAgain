package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.AppleSignupUseCase.AppleSignupCommand;
import com.triagain.user.port.in.AppleSignupUseCase.AppleSignupResult;
import com.triagain.user.port.out.AppleTokenVerifierPort;
import com.triagain.user.port.out.AppleTokenVerifierPort.AppleUserInfo;
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
class AppleSignupServiceTest {

    @InjectMocks
    private AppleSignupService appleSignupService;

    @Mock
    private AppleTokenVerifierPort appleTokenVerifierPort;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private JwtProvider jwtProvider;

    private AppleUserInfo appleUserInfo;

    @BeforeEach
    void setUp() {
        appleUserInfo = new AppleUserInfo("001234.abcdef.5678", "apple@privaterelay.appleid.com");
    }

    @Test
    @DisplayName("정상 회원가입 — 유저 생성 + JWT 발급")
    void signup_success() {
        // Given
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(appleUserInfo);
        given(userRepositoryPort.findById("001234.abcdef.5678")).willReturn(Optional.empty());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        AppleSignupCommand command = new AppleSignupCommand(
                "valid-token", "001234.abcdef.5678", "내닉네임", true);

        // When
        AppleSignupResult result = appleSignupService.signup(command);

        // Then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(1800L);
        assertThat(result.user().id()).isEqualTo("001234.abcdef.5678");
        assertThat(result.user().nickname()).isEqualTo("내닉네임");
        assertThat(result.user().profileImageUrl()).isNull();
        verify(userRepositoryPort).save(any(User.class));
    }

    @Test
    @DisplayName("약관 미동의 — TERMS_NOT_AGREED 에러")
    void signup_termsNotAgreed_throwsException() {
        // Given
        AppleSignupCommand command = new AppleSignupCommand(
                "valid-token", "001234.abcdef.5678", "내닉네임", false);

        // When & Then
        assertThatThrownBy(() -> appleSignupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TERMS_NOT_AGREED);

        verify(appleTokenVerifierPort, never()).verify(anyString());
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("이미 가입된 유저 — USER_ALREADY_EXISTS 에러 (409)")
    void signup_alreadyExists_throwsException() {
        // Given
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(appleUserInfo);
        given(userRepositoryPort.findById("001234.abcdef.5678")).willReturn(
                Optional.of(User.of("001234.abcdef.5678", "APPLE", "apple@test.com", "기존유저", null,
                        java.time.LocalDateTime.now(), java.time.LocalDateTime.now()))
        );

        AppleSignupCommand command = new AppleSignupCommand(
                "valid-token", "001234.abcdef.5678", "내닉네임", true);

        // When & Then
        assertThatThrownBy(() -> appleSignupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_ALREADY_EXISTS);

        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("appleId 불일치 — APPLE_ID_MISMATCH 에러")
    void signup_appleIdMismatch_throwsException() {
        // Given
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(appleUserInfo);

        AppleSignupCommand command = new AppleSignupCommand(
                "valid-token", "wrong-apple-id", "내닉네임", true);

        // When & Then
        assertThatThrownBy(() -> appleSignupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPLE_ID_MISMATCH);

        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("유효하지 않은 Apple 토큰 — INVALID_APPLE_TOKEN 에러")
    void signup_invalidToken_throwsException() {
        // Given
        given(appleTokenVerifierPort.verify("invalid-token"))
                .willThrow(new BusinessException(ErrorCode.INVALID_APPLE_TOKEN));

        AppleSignupCommand command = new AppleSignupCommand(
                "invalid-token", "001234.abcdef.5678", "내닉네임", true);

        // When & Then
        assertThatThrownBy(() -> appleSignupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    @DisplayName("닉네임 1자 — INVALID_NICKNAME 에러")
    void signup_nicknameTooShort_throwsException() {
        // Given
        AppleSignupCommand command = new AppleSignupCommand(
                "valid-token", "001234.abcdef.5678", "가", true);

        // When & Then
        assertThatThrownBy(() -> appleSignupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NICKNAME);
    }

    @Test
    @DisplayName("닉네임 빈값 — NICKNAME_REQUIRED 에러")
    void signup_nicknameBlank_throwsException() {
        // Given
        AppleSignupCommand command = new AppleSignupCommand(
                "valid-token", "001234.abcdef.5678", "   ", true);

        // When & Then
        assertThatThrownBy(() -> appleSignupService.signup(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_REQUIRED);
    }

    @Test
    @DisplayName("닉네임 앞뒤 공백 트림 후 정상 가입")
    void signup_nicknameWithSpaces_trimsAndSucceeds() {
        // Given
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(appleUserInfo);
        given(userRepositoryPort.findById("001234.abcdef.5678")).willReturn(Optional.empty());
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        AppleSignupCommand command = new AppleSignupCommand(
                "valid-token", "001234.abcdef.5678", "  내닉네임  ", true);

        // When
        AppleSignupResult result = appleSignupService.signup(command);

        // Then
        assertThat(result.user().nickname()).isEqualTo("내닉네임");
    }
}
