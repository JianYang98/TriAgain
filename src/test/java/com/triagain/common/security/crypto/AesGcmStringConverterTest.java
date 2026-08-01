package com.triagain.common.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmStringConverterTest {

	private static final String TEST_KEY_BASE64 =
			Base64.getEncoder().encodeToString("test-key-32-bytes-for-aes-256-!!".getBytes());

	private AesGcmStringConverter converter;

	@BeforeEach
	void setUp() {
		converter = new AesGcmStringConverter(TEST_KEY_BASE64);
		converter.initialize();
	}

	@Test
	@DisplayName("암호화 후 복호화하면 원본 평문이 복원된다")
	void roundTrip_returnsOriginalPlaintext() {
		// Given
		String plaintext = "rt_apple_abcdef123456";

		// When
		String encrypted = converter.convertToDatabaseColumn(plaintext);
		String decrypted = converter.convertToEntityAttribute(encrypted);

		// Then
		assertThat(decrypted).isEqualTo(plaintext);
	}

	@Test
	@DisplayName("암호화된 결과는 v1: prefix + Base64 형태이고 평문이 노출되지 않는다")
	void encrypted_hasVersionPrefix_andDoesNotExposePlaintext() {
		// Given
		String plaintext = "rt_apple_secret_token";

		// When
		String encrypted = converter.convertToDatabaseColumn(plaintext);

		// Then
		assertThat(encrypted).startsWith("v1:");
		assertThat(encrypted).doesNotContain(plaintext);
	}

	@Test
	@DisplayName("동일 평문을 두 번 암호화하면 IV가 달라 매번 다른 ciphertext가 나온다")
	void sameInput_producesDifferentCiphertext_dueToRandomIv() {
		// Given
		String plaintext = "same-token";

		// When
		String encrypted1 = converter.convertToDatabaseColumn(plaintext);
		String encrypted2 = converter.convertToDatabaseColumn(plaintext);

		// Then
		assertThat(encrypted1).isNotEqualTo(encrypted2);
		// 둘 다 동일 평문으로 복호화돼야 함
		assertThat(converter.convertToEntityAttribute(encrypted1)).isEqualTo(plaintext);
		assertThat(converter.convertToEntityAttribute(encrypted2)).isEqualTo(plaintext);
	}

	@Test
	@DisplayName("ciphertext가 변조되면 GCM 인증 실패로 예외가 발생한다")
	void tamperedCiphertext_throwsException() {
		// Given
		String encrypted = converter.convertToDatabaseColumn("original");

		// 마지막 문자(tag 영역) 한 글자 변조
		char lastChar = encrypted.charAt(encrypted.length() - 1);
		char swapped = (lastChar == 'A') ? 'B' : 'A';
		String tampered = encrypted.substring(0, encrypted.length() - 1) + swapped;

		// When & Then
		assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("복호화");
	}

	@Test
	@DisplayName("null 입력은 null로 통과 (Apple 외 사용자/탈퇴 후 nullable 컬럼 호환)")
	void nullInput_passesThrough() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}

	@Test
	@DisplayName("v1: prefix가 없는 값(평문 등)은 복호화 시 예외")
	void missingVersionPrefix_throwsException() {
		assertThatThrownBy(() -> converter.convertToEntityAttribute("plaintext-no-prefix"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("버전 prefix");
	}

	@Test
	@DisplayName("키 길이가 32바이트가 아니면 부팅 시 예외 (fail-fast)")
	void invalidKeyLength_failsFast() {
		// Given — 16바이트 키
		String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
		AesGcmStringConverter badConverter = new AesGcmStringConverter(shortKey);

		// When & Then
		assertThatThrownBy(badConverter::initialize)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32바이트");
	}

	@Test
	@DisplayName("키가 비어있으면 부팅 시 예외 (fail-fast)")
	void blankKey_failsFast() {
		AesGcmStringConverter badConverter = new AesGcmStringConverter("");
		assertThatThrownBy(badConverter::initialize)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("설정되지 않았습니다");
	}
}