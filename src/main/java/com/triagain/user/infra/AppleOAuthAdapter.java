package com.triagain.user.infra;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.user.port.out.AppleOAuthPort;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Apple OAuth 어댑터 — authorizationCode → refresh_token 교환 + refresh_token revoke 호출.
 *
 * <p>Apple은 OAuth 표준 client_secret을 고정 문자열로 제공하지 않고, 매 요청마다
 * Team ID + Key ID + .p8 private key로 서명한 ES256 JWT를 client_secret으로 사용해야 한다.
 * 이 어댑터는 호출 시점마다 5분 만료 JWT를 즉석 생성한다 (캐싱 없음).
 *
 * <p>환경변수가 비어 있으면 disabled 모드로 동작한다 (로컬 dev/test에서는 정상).
 * disabled 모드에서는 isEnabled()가 false를 반환하고, exchange/revoke 호출은 WARN 로그 후
 * exchange는 예외를 던지고 revoke는 조용히 무시한다.
 */
@Component
public class AppleOAuthAdapter implements AppleOAuthPort {

	private static final Logger log = LoggerFactory.getLogger(AppleOAuthAdapter.class);
	private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
	private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
	private static final String APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
	private static final long CLIENT_SECRET_EXPIRATION_SECONDS = 300L; // 5분

	private final RestClient restClient;
	private final String clientId;
	private final String teamId;
	private final String keyId;
	private final String privateKeyPem;

	private ECPrivateKey privateKey;
	private boolean enabled;

	public AppleOAuthAdapter(
			RestClient restClient,
			@Value("${apple.client-id}") String clientId,
			@Value("${apple.team-id:}") String teamId,
			@Value("${apple.key-id:}") String keyId,
			@Value("${apple.private-key:}") String privateKeyPem
	) {
		this.restClient = restClient;
		this.clientId = clientId;
		this.teamId = teamId;
		this.keyId = keyId;
		this.privateKeyPem = privateKeyPem;
	}

	/** 빈 초기화 시 환경변수와 .p8 키 파싱을 검증 — fail-fast로 부팅 단계에서 잘못된 설정을 잡는다 */
	@PostConstruct
	void initialize() {
		boolean allBlank = isBlank(teamId) && isBlank(keyId) && isBlank(privateKeyPem);
		boolean anyBlank = isBlank(teamId) || isBlank(keyId) || isBlank(privateKeyPem);

		if (allBlank) {
			this.enabled = false;
			log.warn("Apple OAuth disabled — APPLE_TEAM_ID/APPLE_KEY_ID/APPLE_PRIVATE_KEY 미설정. 회원가입 시 APPLE_TOKEN_EXCHANGE_ERROR 발생, 탈퇴 시 revoke 미호출");
			return;
		}
		if (anyBlank) {
			throw new IllegalStateException(
					"Apple OAuth 설정 누락 — APPLE_TEAM_ID/APPLE_KEY_ID/APPLE_PRIVATE_KEY 중 일부만 채워져 있다. 셋 다 설정하거나 셋 다 비워야 한다");
		}

		try {
			this.privateKey = parsePrivateKey(privateKeyPem);
		} catch (Exception e) {
			throw new IllegalStateException("Apple OAuth .p8 private key 파싱 실패", e);
		}
		this.enabled = true;
		log.info("Apple OAuth enabled — teamId={}, keyId={}, clientId={}", teamId, keyId, clientId);
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	/** authorizationCode → refresh_token 교환 (회원가입 차단형). disabled면 예외 */
	@Override
	public String exchangeAuthorizationCode(String authorizationCode) {
		if (!enabled) {
			log.warn("Apple OAuth disabled 상태에서 exchangeAuthorizationCode 호출됨");
			throw new BusinessException(ErrorCode.APPLE_TOKEN_EXCHANGE_ERROR);
		}

		try {
			String clientSecret = generateClientSecret();
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("client_id", clientId);
			form.add("client_secret", clientSecret);
			form.add("code", authorizationCode);
			form.add("grant_type", "authorization_code");

			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.post()
					.uri(APPLE_TOKEN_URL)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(Map.class);

			if (response == null) {
				log.error("Apple /auth/token 응답이 null");
				throw new BusinessException(ErrorCode.APPLE_TOKEN_EXCHANGE_ERROR);
			}
			Object refreshToken = response.get("refresh_token");
			if (refreshToken == null) {
				log.error("Apple /auth/token 응답에 refresh_token 없음: keys={}", response.keySet());
				throw new BusinessException(ErrorCode.APPLE_TOKEN_EXCHANGE_ERROR);
			}
			return refreshToken.toString();
		} catch (BusinessException e) {
			throw e;
		} catch (RestClientException e) {
			log.error("Apple /auth/token 호출 실패: {}", e.getMessage());
			throw new BusinessException(ErrorCode.APPLE_TOKEN_EXCHANGE_ERROR);
		} catch (Exception e) {
			log.error("Apple /auth/token 처리 중 오류: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.APPLE_TOKEN_EXCHANGE_ERROR);
		}
	}

	/** Apple refresh_token 무효화 — 회원탈퇴 시. 실패해도 탈퇴는 graceful 진행. App Store 5.1.1(v) */
	@Override
	public void revokeRefreshToken(String refreshToken) {
		if (!enabled) {
			log.warn("Apple OAuth disabled 상태 — revoke 미호출");
			return;
		}
		if (refreshToken == null || refreshToken.isBlank()) {
			log.warn("Apple refresh_token이 비어 있음 — revoke 미호출");
			return;
		}

		try {
			String clientSecret = generateClientSecret();
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("client_id", clientId);
			form.add("client_secret", clientSecret);
			form.add("token", refreshToken);
			form.add("token_type_hint", "refresh_token");

			restClient.post()
					.uri(APPLE_REVOKE_URL)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.toBodilessEntity();

			log.info("Apple refresh_token revoke 성공");
		} catch (Exception e) {
			log.warn("Apple refresh_token revoke 실패 (탈퇴는 계속 진행): {}", e.getMessage());
		}
	}

	/** Apple Client Secret JWT 생성 — ES256, exp = now + 5분, 매 호출마다 즉석 생성 */
	private String generateClientSecret() {
		Instant now = Instant.now();
		return Jwts.builder()
				.header().keyId(keyId).and()
				.issuer(teamId)
				.audience().add(APPLE_AUDIENCE).and()
				.subject(clientId)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(CLIENT_SECRET_EXPIRATION_SECONDS)))
				.signWith(privateKey, Jwts.SIG.ES256)
				.compact();
	}

	/** .p8 PEM 문자열 → ECPrivateKey 객체 (환경변수의 \n 이스케이프 처리 포함) */
	private static ECPrivateKey parsePrivateKey(String pem) throws Exception {
		String normalized = pem
				.replace("\\n", "\n")
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s+", "");
		byte[] decoded = Base64.getDecoder().decode(normalized);
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
		return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
