package com.triagain.user.application;

import com.triagain.common.auth.JwtProvider;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.domain.model.User;
import com.triagain.user.port.in.RefreshTokenUseCase.RefreshCommand;
import com.triagain.user.port.in.RefreshTokenUseCase.RefreshResult;
import com.triagain.user.port.out.UserRepositoryPort;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Test
    @DisplayName("정상 Refresh Token — 새 Access Token 발급")
    void refresh_validToken_returnsNewAccessToken() {
        // Given
        String refreshToken = "valid-refresh-token";
        User user = User.of("user-123", "KAKAO", "test@test.com", "테스트", null, LocalDateTime.now(), LocalDateTime.now());

        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getTokenType(refreshToken)).willReturn("refresh");
        given(jwtProvider.getUserId(refreshToken)).willReturn("user-123");
        given(userRepositoryPort.findById("user-123")).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken("user-123", "KAKAO")).willReturn("new-access-token");
        given(jwtProvider.getAccessTokenExpirationSeconds()).willReturn(1800L);

        // When
        RefreshResult result = refreshTokenService.refresh(new RefreshCommand(refreshToken));

        // Then
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("만료된 Refresh Token — INVALID_REFRESH_TOKEN 예외")
    void refresh_expiredToken_throwsException() {
        // Given
        String expiredToken = "expired-refresh-token";
        given(jwtProvider.validateToken(expiredToken)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand(expiredToken)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Access Token을 Refresh Token으로 사용 시 — INVALID_REFRESH_TOKEN 예외")
    void refresh_accessTokenUsedAsRefresh_throwsException() {
        // Given
        String accessToken = "access-token";
        given(jwtProvider.validateToken(accessToken)).willReturn(true);
        given(jwtProvider.getTokenType(accessToken)).willReturn("access");

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand(accessToken)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("삭제된 유저의 Refresh Token — USER_NOT_FOUND 예외")
    void refresh_deletedUser_throwsException() {
        // Given
        String refreshToken = "valid-refresh-token";
        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getTokenType(refreshToken)).willReturn("refresh");
        given(jwtProvider.getUserId(refreshToken)).willReturn("deleted-user");
        given(userRepositoryPort.findById("deleted-user")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand(refreshToken)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}