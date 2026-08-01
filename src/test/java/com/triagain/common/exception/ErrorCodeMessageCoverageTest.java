package com.triagain.common.exception;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 모든 {@link ErrorCode}가 error-messages.properties에 메시지를 갖는지 전수 검증 — M2 (step4 §8-5, impl-guards G-7).
 * <p>
 * 운영 설정({@code application.yml}의 {@code spring.messages.basename=error-messages})과 동일한 basename으로
 * {@link ResourceBundleMessageSource}를 직접 구성한다 — 전체 스프링 컨텍스트 없이도 실제 properties 파일을 그대로
 * 검증하는 슬라이스 테스트다.
 * <p>
 * 🔴 기본값 인자를 넘기지 않는 {@link MessageSource#getMessage(String, Object[], Locale)} 오버로드를 써야 한다.
 * {@code GlobalExceptionHandler.resolveMessage()}처럼 기본값(코드명)을 넘기면 fallback이 동작해 메시지 누락이
 * 조용히 통과한다 — 그 fallback 자체가 이 테스트가 잡으려는 버그의 은폐 경로다.
 */
class ErrorCodeMessageCoverageTest {

	private MessageSource messageSource;

	@BeforeEach
	void setUp() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("error-messages");
		source.setDefaultEncoding("UTF-8");
		messageSource = source;
	}

	@Test
	@DisplayName("모든 ErrorCode는 error-messages.properties에 메시지를 갖는다")
	void allErrorCodesHaveMessages() {
		for (ErrorCode code : ErrorCode.values()) {
			assertThatCode(() -> messageSource.getMessage(code.name(), null, Locale.getDefault()))
					.as("ErrorCode %s 메시지 누락", code.name())
					.doesNotThrowAnyException();
		}
	}
}
