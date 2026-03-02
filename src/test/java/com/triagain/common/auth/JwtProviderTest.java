package com.triagain.common.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-secret-key-for-local-development-only-triagain".getBytes());
    private static final long ACCESS_TOKEN_EXPIRATION = 1800000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 1209600000L;

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
    }

    @Test
    @DisplayName("Access Token 생성 후 userId를 추출할 수 있다")
    void createAccessToken_thenExtractUserId() {
        // Given
        String userId = "user-123";
        String provider = "KAKAO";

        // When
        String token = jwtProvider.createAccessToken(userId, provider);

        // Then
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("Refresh Token 생성 후 userId를 추출할 수 있다")
    void createRefreshToken_thenExtractUserId() {
        // Given
        String userId = "user-456";

        // When
        String token = jwtProvider.createRefreshToken(userId);

        // Then
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("만료된 토큰은 유효하지 않다")
    void expiredToken_isInvalid() {
        // Given — 만료 시간을 0으로 설정
        JwtProvider expiredProvider = new JwtProvider(SECRET, 0L, 0L);

        // When
        String token = expiredProvider.createAccessToken("user-123", "KAKAO");

        // Then
        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("위조된 토큰은 유효하지 않다")
    void tamperedToken_isInvalid() {
        // Given
        String token = jwtProvider.createAccessToken("user-123", "KAKAO");
        String tampered = token + "tampered";

        // Then
        assertThat(jwtProvider.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 유효하지 않다")
    void differentKeyToken_isInvalid() {
        // Given
        String otherSecret = Base64.getEncoder().encodeToString(
                "other-secret-key-that-is-different-from-original-key".getBytes());
        JwtProvider otherProvider = new JwtProvider(otherSecret, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
        String token = otherProvider.createAccessToken("user-123", "KAKAO");

        // Then
        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Access Token의 type 클레임은 'access'이다")
    void accessToken_hasAccessType() {
        // Given
        String token = jwtProvider.createAccessToken("user-123", "KAKAO");

        // When
        String type = jwtProvider.getTokenType(token);

        // Then
        assertThat(type).isEqualTo("access");
    }

    @Test
    @DisplayName("Refresh Token의 type 클레임은 'refresh'이다")
    void refreshToken_hasRefreshType() {
        // Given
        String token = jwtProvider.createRefreshToken("user-123");

        // When
        String type = jwtProvider.getTokenType(token);

        // Then
        assertThat(type).isEqualTo("refresh");
    }

    @Test
    @DisplayName("Access Token 만료 시간(초)을 반환한다")
    void getAccessTokenExpirationSeconds() {
        assertThat(jwtProvider.getAccessTokenExpirationSeconds()).isEqualTo(1800L);
    }
}
