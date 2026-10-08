package com.triagain.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.restassured.RestAssured;

/** 실제 HTTP ERROR 재디스패치가 원래 오류를 401로 가리지 않는지 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ErrorDispatchAuthorizationApiTest.ProbeConfig.class)
class ErrorDispatchAuthorizationApiTest {

	@LocalServerPort
	private int port;

	@Test
	@DisplayName("인증된 요청의 ERROR 재디스패치는 원래 500을 유지한다")
	void authenticatedErrorDispatch_keepsOriginalStatus() {
		// Given: 인증된 요청이 컨테이너의 ERROR 디스패치를 일으키는 테스트 전용 경로
		// When
		var response = RestAssured.given().port(port)
				.header("X-User-Id", "probe-user")
				.when().get("/__error-dispatch-probe");

		// Then: ERROR 허용을 제거하면 이 500이 401로 바뀐다.
		assertThat(response.statusCode()).isEqualTo(500);
	}

	@Test
	@DisplayName("무인증 원 요청은 ERROR 경로에 들어가기 전에 401로 거부한다")
	void unauthenticatedRequest_isRejectedBeforeErrorDispatch() {
		// Given: 인증 헤더가 없는 원 요청
		// When
		var response = RestAssured.given().port(port)
				.when().get("/__error-dispatch-probe");

		// Then
		assertThat(response.statusCode()).isEqualTo(401);
	}

	@TestConfiguration
	static class ProbeConfig {
		@Bean
		ProbeController probeController() {
			return new ProbeController();
		}
	}

	@RestController
	static class ProbeController {
		@GetMapping("/__error-dispatch-probe")
		void error(HttpServletResponse response) throws IOException {
			response.sendError(500);
		}
	}
}
