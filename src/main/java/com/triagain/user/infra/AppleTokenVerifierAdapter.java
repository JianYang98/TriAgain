package com.triagain.user.infra;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.port.out.AppleTokenVerifierPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AppleTokenVerifierAdapter implements AppleTokenVerifierPort {

    private static final Logger log = LoggerFactory.getLogger(AppleTokenVerifierAdapter.class);
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final long JWKS_CACHE_TTL_MS = 24 * 60 * 60 * 1000L;

    private final RestClient restClient;
    private final String clientId;

    private volatile Map<String, PublicKey> cachedKeys = new ConcurrentHashMap<>();
    private volatile long cacheTimestamp = 0;

    public AppleTokenVerifierAdapter(
            RestClient restClient,
            @Value("${apple.client-id}") String clientId
    ) {
        this.restClient = restClient;
        this.clientId = clientId;
    }

    /** Apple Identity Token JWT 검증 — JWKS 공개키로 RS256 서명 검증 후 sub/email 반환 */
    @Override
    public AppleUserInfo verify(String identityToken) {
        try {
            String kid = extractKid(identityToken);
            PublicKey publicKey = getPublicKey(kid);

            Claims claims = Jwts.parser()
                    .requireIssuer(APPLE_ISSUER)
                    .requireAudience(clientId)
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            String sub = claims.getSubject();
            String email = claims.get("email", String.class);

            return new AppleUserInfo(sub, email);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Apple 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple 토큰 검증 중 오류: {}", e.getMessage());
            throw new BusinessException(ErrorCode.APPLE_TOKEN_VERIFICATION_ERROR);
        }
    }

    /** JWT 헤더에서 kid 추출 — JWKS 키 매칭에 사용 */
    private String extractKid(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        }
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            @SuppressWarnings("unchecked")
            Map<String, Object> header = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(headerJson, Map.class);
            return (String) header.get("kid");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    /** kid로 공개키 조회 — 캐시 hit/miss/refresh 처리 */
    private PublicKey getPublicKey(String kid) {
        if (isCacheValid() && cachedKeys.containsKey(kid)) {
            return cachedKeys.get(kid);
        }

        refreshJwks();

        PublicKey key = cachedKeys.get(kid);
        if (key == null) {
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        }
        return key;
    }

    private boolean isCacheValid() {
        return !cachedKeys.isEmpty()
                && (System.currentTimeMillis() - cacheTimestamp) < JWKS_CACHE_TTL_MS;
    }

    /** Apple JWKS 엔드포인트에서 공개키 fetch 및 캐시 갱신 */
    @SuppressWarnings("unchecked")
    private void refreshJwks() {
        try {
            Map<String, Object> jwks = restClient.get()
                    .uri(APPLE_JWKS_URL)
                    .retrieve()
                    .body(Map.class);

            if (jwks == null || !jwks.containsKey("keys")) {
                throw new BusinessException(ErrorCode.APPLE_TOKEN_VERIFICATION_ERROR);
            }

            Map<String, PublicKey> newKeys = new ConcurrentHashMap<>();
            List<Map<String, String>> keys = (List<Map<String, String>>) jwks.get("keys");

            for (Map<String, String> keyData : keys) {
                String kid = keyData.get("kid");
                PublicKey publicKey = buildRsaPublicKey(keyData.get("n"), keyData.get("e"));
                newKeys.put(kid, publicKey);
            }

            cachedKeys = newKeys;
            cacheTimestamp = System.currentTimeMillis();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Apple JWKS fetch 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.APPLE_TOKEN_VERIFICATION_ERROR);
        }
    }

    /** JWKS n, e 값으로 RSA 공개키 생성 */
    private PublicKey buildRsaPublicKey(String n, String e) {
        try {
            byte[] nBytes = Base64.getUrlDecoder().decode(n);
            byte[] eBytes = Base64.getUrlDecoder().decode(e);
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, nBytes),
                    new BigInteger(1, eBytes)
            );
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.APPLE_TOKEN_VERIFICATION_ERROR);
        }
    }
}
