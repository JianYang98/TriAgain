package com.triagain.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * 비운영(DevSecurityConfig) 지표 노출·GC API — 대시보드 쿼리가 기대는 이름·라벨을 실제 스크랩 본문으로 확인.
 * {@code @SpringBootTest}는 지표 export를 기본으로 끄므로(Prometheus 레지스트리 미생성) 다시 켠다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false)
@ActiveProfiles("test")
class PrometheusEndpointApiTest {

	@LocalServerPort
	private int port;

	@Test
	@DisplayName("요청을 처리한 뒤 /actuator/prometheus를 인증 없이 부르면 200과 대시보드용 지표를 노출한다")
	void prometheusExposesDashboardMetrics() {
		// given — HTTP 히스토그램 버킷은 요청이 한 번 기록돼야 나타난다
		RestAssured.given().port(port).when().get("/health").then().statusCode(200);

		// when
		Response response = RestAssured.given().port(port).when().get("/actuator/prometheus");

		// then
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.asString())
				.contains("application=\"triagain\"")
				.contains("http_server_requests_seconds_bucket{")
				.contains("hikaricp_connections_active{")
				.contains("hikaricp_connections_acquire_seconds_bucket{")
				.contains("hikaricp_connections_usage_seconds_count{")
				.contains("hikaricp_connections_timeout_total{")
				.contains("tomcat_threads_busy_threads{")
				.contains("executor_pool_size_threads{application=\"triagain\",name=\"taskScheduler\"}");
	}

	@Test
	@DisplayName("비운영에서는 API 키 없이 POST /internal/gc가 200과 힙 사용량을 반환한다")
	void gcTriggerReturnsHeapUsageWithoutKeyInNonProd() {
		// when
		Response response = RestAssured.given().port(port).when().post("/internal/gc");

		// then
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.jsonPath().getBoolean("success")).isTrue();
		assertThat(response.jsonPath().getLong("data.durationMs")).isGreaterThanOrEqualTo(0);
		assertThat(response.jsonPath().getLong("data.heapUsedAfterMb")).isGreaterThanOrEqualTo(0);
	}
}
