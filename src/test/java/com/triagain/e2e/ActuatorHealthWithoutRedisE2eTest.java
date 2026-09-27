package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/** Redis starter가 있어도 DB 전략은 Redis 없이 실제 HTTP /actuator/health 200 UP — 배포 헬스체크(curl -f) 회귀 방지 */
class ActuatorHealthWithoutRedisE2eTest extends E2eTestBase {

	@Test
	@DisplayName("DB 전략에서 Redis가 없어도 /actuator/health는 200 UP이다")
	void health_isUpWithoutRedis() {
		// Given: E2eTestBase가 Redis를 닫힌 포트로 지정 (integration 프로필 = PESSIMISTIC)

		// When
		ExtractableResponse<Response> response = givenRequest().when().get("/actuator/health").then().extract();

		// Then
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
	}
}
