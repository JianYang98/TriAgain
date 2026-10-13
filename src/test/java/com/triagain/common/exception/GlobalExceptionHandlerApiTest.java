package com.triagain.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import io.restassured.RestAssured;
import io.restassured.response.Response;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GlobalExceptionHandlerApiTest {

	private static final String INVALID_INPUT_MESSAGE = "잘못된 입력값입니다.";

	@LocalServerPort
	private int port;

	@Test
	@DisplayName("필수 query 파라미터가 누락되면 400 C001을 반환한다")
	void missingQueryParameterReturnsInvalidInput() {
		Response response = RestAssured.given()
				.port(port)
				.when()
				.put("/internal/upload-sessions/complete");

		assertInvalidInput(response);
	}

	@Test
	@DisplayName("query 파라미터 타입이 잘못되면 400 C001을 반환한다")
	void invalidQueryParameterTypeReturnsInvalidInput() {
		Response response = RestAssured.given()
				.port(port)
				.queryParam("page", "abc")
				.when()
				.get("/crews/search");

		assertInvalidInput(response);
	}

	private void assertInvalidInput(Response response) {
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.jsonPath().getBoolean("success")).isFalse();
		assertThat((Object) response.jsonPath().get("data")).isNull();
		assertThat(response.jsonPath().getString("error.code")).isEqualTo("C001");
		assertThat(response.jsonPath().getString("error.message")).isEqualTo(INVALID_INPUT_MESSAGE);
	}
}
