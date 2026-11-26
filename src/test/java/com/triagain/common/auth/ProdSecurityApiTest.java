package com.triagain.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * 운영 SecurityConfig 경계 — prod 프로필을 실제로 올려 지표·health 공개와 /internal/** 키 검사를 확인.
 * DB는 H2로 대체하고, prod yml이 환경변수로 요구하는 값만 테스트 값으로 채운다.
 * {@code @SpringBootTest}는 지표 export를 기본으로 끄므로(Prometheus 레지스트리 미생성) 다시 켠다.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.datasource.url=jdbc:h2:mem:prodsecurity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
			"spring.datasource.username=sa",
			"spring.datasource.password=",
			"spring.datasource.driver-class-name=org.h2.Driver",
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1sb2NhbC1kZXZlbG9wbWVudC1vbmx5LXRyaWFnYWlu",
			"app.crypto.apple-refresh-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLWFlcy0yNTYtISE=",
			"internal.api-key=prod-security-test-key"
		})
@AutoConfigureObservability(tracing = false)
@ActiveProfiles({"test", "prod"})
class ProdSecurityApiTest {

	@LocalServerPort
	private int port;

	@LocalManagementPort
	private int managementPort;

	@Test
	@DisplayName("운영에서 /actuator/prometheus는 인증 없이 200을 반환한다 — 외부 차단은 nginx·보안그룹이 맡는다")
	void prometheusIsPermittedWithoutAuthInProd() {
		// when
		Response response = RestAssured.given().port(managementPort).when().get("/actuator/prometheus");

		// then
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.asString()).contains("application=\"triagain\"");
	}

	@Test
	@DisplayName("운영에서 /actuator/health는 인증 없이 200 UP이다 — 배포 헬스체크 유지")
	void healthStaysPublicInProd() {
		// when
		Response response = RestAssured.given().port(managementPort).when().get("/actuator/health");

		// then
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
	}

	@ParameterizedTest
	@ValueSource(strings = {"/actuator/env", "/actuator/configprops"})
	@DisplayName("운영에서 노출 목록 밖의 actuator 경로는 인증 없이 열리지 않는다")
	void otherActuatorEndpointsStayClosedInProd(String path) {
		// when
		Response response = RestAssured.given().port(managementPort).when().get(path);

		// then
		assertThat(response.statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("서비스 포트에서는 /actuator/prometheus를 응답하지 않는다 — 지표는 관리 포트 전용")
	void prometheusIsNotServedOnServicePort() {
		// when
		Response response = RestAssured.given().port(port).when().get("/actuator/prometheus");

		// then
		assertThat(response.statusCode()).isNotEqualTo(200);
	}

	@Test
	@DisplayName("운영에서 API 키 없이 POST /internal/gc를 부르면 403이다")
	void gcTriggerRequiresInternalKeyInProd() {
		// when
		Response response = RestAssured.given().port(port).when().post("/internal/gc");

		// then
		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.jsonPath().getString("error.code")).isEqualTo("FORBIDDEN");
	}
}
