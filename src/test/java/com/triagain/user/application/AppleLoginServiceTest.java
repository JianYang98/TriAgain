package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.AppleLoginUseCase.AppleLoginCommand;
import com.triagain.user.port.in.AppleLoginUseCase.AppleLoginResult;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppleLoginServiceTest {

    @InjectMocks
    private AppleLoginService appleLoginService;

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
    @DisplayName("기존 유저 — isNewUser=false + JWT 발급")
    void login_existingUser_returnsJwt() {
        // Given
        User existingUser = User.of("001234.abcdef.5678", "APPLE", "apple@test.com", "애플유저", null,
                LocalDateTime.now(), LocalDateTime.now());
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(appleUserInfo);
        given(userRepositoryPort.findById("001234.abcdef.5678")).willReturn(Optional.of(existingUser));
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        // When
        AppleLoginResult result = appleLoginService.login(new AppleLoginCommand("valid-token"));

        // Then
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.user().id()).isEqualTo("001234.abcdef.5678");
        assertThat(result.user().nickname()).isEqualTo("애플유저");
        assertThat(result.appleId()).isNull();
    }

    @Test
    @DisplayName("신규 유저 — isNewUser=true + appleId/email 반환, JWT 미발급")
    void login_newUser_returnsAppleIdWithoutJwt() {
        // Given
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(appleUserInfo);
        given(userRepositoryPort.findById("001234.abcdef.5678")).willReturn(Optional.empty());

        // When
        AppleLoginResult result = appleLoginService.login(new AppleLoginCommand("valid-token"));

        // Then
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.appleId()).isEqualTo("001234.abcdef.5678");
        assertThat(result.email()).isEqualTo("apple@privaterelay.appleid.com");
        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        assertThat(result.user()).isNull();
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("기존 유저 email 변경 — syncAppleProfile 후 save 호출")
    void login_existingUser_emailChanged_saves() {
        // Given
        User existingUser = User.of("001234.abcdef.5678", "APPLE", "old@test.com", "애플유저", null,
                LocalDateTime.now(), LocalDateTime.now());
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(appleUserInfo);
        given(userRepositoryPort.findById("001234.abcdef.5678")).willReturn(Optional.of(existingUser));
        given(userRepositoryPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        // When
        appleLoginService.login(new AppleLoginCommand("valid-token"));

        // Then
        verify(userRepositoryPort).save(any(User.class));
    }

    @Test
    @DisplayName("기존 유저 email null (재로그인) — 기존 email 유지, save 호출 안 함")
    void login_existingUser_nullEmail_preservesExisting() {
        // Given
        AppleUserInfo noEmailUser = new AppleUserInfo("001234.abcdef.5678", null);
        User existingUser = User.of("001234.abcdef.5678", "APPLE", "existing@test.com", "애플유저", null,
                LocalDateTime.now(), LocalDateTime.now());
        given(appleTokenVerifierPort.verify("valid-token")).willReturn(noEmailUser);
        given(userRepositoryPort.findById("001234.abcdef.5678")).willReturn(Optional.of(existingUser));
        given(jwtProvider.createAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        // When
        AppleLoginResult result = appleLoginService.login(new AppleLoginCommand("valid-token"));

        // Then
        assertThat(result.isNewUser()).isFalse();
        verify(userRepositoryPort, never()).save(any(User.class));
    }

    @Test
    @DisplayName("유효하지 않은 Apple 토큰 — INVALID_APPLE_TOKEN 예외")
    void login_invalidToken_throwsException() {
        // Given
        given(appleTokenVerifierPort.verify("invalid-token"))
                .willThrow(new BusinessException(ErrorCode.INVALID_APPLE_TOKEN));

        // When & Then
        assertThatThrownBy(() -> appleLoginService.login(new AppleLoginCommand("invalid-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    @DisplayName("Apple 토큰 검증 오류 — APPLE_TOKEN_VERIFICATION_ERROR 예외")
    void login_verificationError_throwsException() {
        // Given
        given(appleTokenVerifierPort.verify("any-token"))
                .willThrow(new BusinessException(ErrorCode.APPLE_TOKEN_VERIFICATION_ERROR));

        // When & Then
        assertThatThrownBy(() -> appleLoginService.login(new AppleLoginCommand("any-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPLE_TOKEN_VERIFICATION_ERROR);
    }
}
