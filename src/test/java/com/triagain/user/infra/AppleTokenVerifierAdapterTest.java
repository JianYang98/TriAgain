package com.triagain.user.infra;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.port.out.AppleTokenVerifierPort.AppleUserInfo;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@Disabled("Apple 로그인 미연동 — 연동 시 활성화")
@ExtendWith(MockitoExtension.class)
class AppleTokenVerifierAdapterTest {

    private AppleTokenVerifierAdapter adapter;
    private KeyPair keyPair;
    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    private RestClient.ResponseSpec responseSpec;

    private static final String CLIENT_ID = "com.triagain.app";
    private static final String KID = "test-kid-123";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        restClient = mock(RestClient.class);
        requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersUriSpec).when(requestHeadersUriSpec).uri(any(String.class));
        doReturn(responseSpec).when(requestHeadersUriSpec).retrieve();

        adapter = new AppleTokenVerifierAdapter(restClient, CLIENT_ID);
    }

    @Test
    @DisplayName("정상 토큰 검증 — sub/email 반환")
    void verify_validToken_returnsAppleUserInfo() {
        // Given
        mockJwksResponse();
        String token = buildValidToken("001234.abcdef.5678", "apple@test.com");

        // When
        AppleUserInfo result = adapter.verify(token);

        // Then
        assertThat(result.sub()).isEqualTo("001234.abcdef.5678");
        assertThat(result.email()).isEqualTo("apple@test.com");
    }

    @Test
    @DisplayName("email 없는 토큰 — sub만 반환, email null")
    void verify_tokenWithoutEmail_returnsNullEmail() {
        // Given
        mockJwksResponse();
        String token = buildValidToken("001234.abcdef.5678", null);

        // When
        AppleUserInfo result = adapter.verify(token);

        // Then
        assertThat(result.sub()).isEqualTo("001234.abcdef.5678");
        assertThat(result.email()).isNull();
    }

    @Test
    @DisplayName("만료된 토큰 — INVALID_APPLE_TOKEN 예외")
    void verify_expiredToken_throwsException() {
        // Given
        mockJwksResponse();
        String token = buildExpiredToken();

        // When & Then
        assertThatThrownBy(() -> adapter.verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    @DisplayName("잘못된 aud — INVALID_APPLE_TOKEN 예외")
    void verify_wrongAudience_throwsException() {
        // Given
        mockJwksResponse();
        String token = buildTokenWithWrongAudience();

        // When & Then
        assertThatThrownBy(() -> adapter.verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    @DisplayName("잘못된 iss — INVALID_APPLE_TOKEN 예외")
    void verify_wrongIssuer_throwsException() {
        // Given
        mockJwksResponse();
        String token = buildTokenWithWrongIssuer();

        // When & Then
        assertThatThrownBy(() -> adapter.verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    @DisplayName("잘못된 형식의 토큰 — INVALID_APPLE_TOKEN 예외")
    void verify_malformedToken_throwsException() {
        // When & Then
        assertThatThrownBy(() -> adapter.verify("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_APPLE_TOKEN);
    }

    private void mockJwksResponse() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        Map<String, Object> jwks = Map.of("keys", List.of(Map.of(
                "kid", KID,
                "kty", "RSA",
                "n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
                "e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray())
        )));
        given(responseSpec.body(Map.class)).willReturn(jwks);
    }

    private String buildValidToken(String sub, String email) {
        Date now = new Date();
        var builder = Jwts.builder()
                .header().keyId(KID).and()
                .subject(sub)
                .issuer("https://appleid.apple.com")
                .audience().add(CLIENT_ID).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256);

        if (email != null) {
            builder.claim("email", email);
        }

        return builder.compact();
    }

    private String buildExpiredToken() {
        Date past = new Date(System.currentTimeMillis() - 7200000);
        return Jwts.builder()
                .header().keyId(KID).and()
                .subject("001234.abcdef.5678")
                .issuer("https://appleid.apple.com")
                .audience().add(CLIENT_ID).and()
                .issuedAt(past)
                .expiration(new Date(past.getTime() + 3600000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String buildTokenWithWrongAudience() {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(KID).and()
                .subject("001234.abcdef.5678")
                .issuer("https://appleid.apple.com")
                .audience().add("wrong.client.id").and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String buildTokenWithWrongIssuer() {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(KID).and()
                .subject("001234.abcdef.5678")
                .issuer("https://wrong-issuer.com")
                .audience().add(CLIENT_ID).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
