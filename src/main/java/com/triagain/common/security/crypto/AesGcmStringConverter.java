package com.triagain.common.security.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 문자열 컬럼 암호화 컨버터 — Apple refresh_token 등 장기 자격증명 저장용.
 * 저장 포맷: "v1:" + Base64(IV(12B) ‖ ciphertext ‖ tag(16B)). v1 prefix는 향후 키 로테이션 대비.
 *
 * 키 주입: 환경변수 APPLE_REFRESH_KEY (Base64 인코딩된 32바이트 = AES-256). 운영은 GitHub Actions Secrets로 주입.
 * Phase 2에서 KMS / Secrets Manager로 승급 예정 (future-considerations).
 *
 * SEC-C1 (2026-04-09 PR review) 대응 — 평문 저장 → AES-GCM 암호화.
 */
@Component
@Converter
public class AesGcmStringConverter implements AttributeConverter<String, String> {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH_BYTES = 12;
	private static final int TAG_LENGTH_BITS = 128;
	private static final int AES_256_KEY_BYTES = 32;
	private static final String VERSION_PREFIX = "v1:";

	private final String base64Key;
	private SecretKey secretKey;
	private final SecureRandom secureRandom = new SecureRandom();

	public AesGcmStringConverter(@Value("${app.crypto.apple-refresh-key}") String base64Key) {
		this.base64Key = base64Key;
	}

	/** 키 길이 검증 — 부적합 시 부팅 실패 (JwtProvider/AppleOAuthAdapter 패턴 차용) */
	@PostConstruct
	void initialize() {
		if (base64Key == null || base64Key.isBlank()) {
			throw new IllegalStateException(
					"app.crypto.apple-refresh-key 환경변수가 설정되지 않았습니다. (운영: APPLE_REFRESH_KEY)");
		}
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(base64Key);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("app.crypto.apple-refresh-key는 Base64 인코딩 문자열이어야 합니다.", e);
		}
		if (keyBytes.length != AES_256_KEY_BYTES) {
			throw new IllegalStateException(
					"app.crypto.apple-refresh-key는 32바이트(AES-256)여야 합니다. 현재: " + keyBytes.length + "바이트");
		}
		this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
	}

	@Override
	public String convertToDatabaseColumn(String attribute) {
		if (attribute == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_LENGTH_BYTES];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));

			ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
			buffer.put(iv);
			buffer.put(ciphertext);

			return VERSION_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
		} catch (Exception e) {
			throw new IllegalStateException("Apple refresh token 암호화 실패", e);
		}
	}

	@Override
	public String convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		if (!dbData.startsWith(VERSION_PREFIX)) {
			throw new IllegalStateException(
					"암호화된 값의 버전 prefix가 없습니다. 평문 또는 알 수 없는 포맷: " + dbData.substring(0, Math.min(10, dbData.length())));
		}
		try {
			byte[] decoded = Base64.getDecoder().decode(dbData.substring(VERSION_PREFIX.length()));
			if (decoded.length <= IV_LENGTH_BYTES) {
				throw new IllegalStateException("암호문 길이가 너무 짧습니다.");
			}

			ByteBuffer buffer = ByteBuffer.wrap(decoded);
			byte[] iv = new byte[IV_LENGTH_BYTES];
			buffer.get(iv);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] plaintext = cipher.doFinal(ciphertext);

			return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("Apple refresh token 복호화 실패", e);
		}
	}
}
