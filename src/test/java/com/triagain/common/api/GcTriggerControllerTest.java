package com.triagain.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseEntity;

import com.triagain.common.api.GcTriggerController.GcTriggerResponse;
import com.triagain.common.response.ApiResponse;

class GcTriggerControllerTest {

	private final GcTriggerController controller = new GcTriggerController();

	@DisplayName("GC 트리거를 호출하면 200과 힙 사용량·소요시간을 반환한다")
	@Test
	void trigger_returns_200_and_heap_metrics() {
		// when
		ResponseEntity<ApiResponse<GcTriggerResponse>> response = controller.trigger();

		// then — GC 직후에도 할당이 계속되므로 before > after는 단정하지 않는다
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody().success()).isTrue();

		GcTriggerResponse body = response.getBody().data();
		assertThat(body.heapUsedBeforeMb()).isGreaterThanOrEqualTo(0);
		assertThat(body.heapUsedAfterMb()).isGreaterThanOrEqualTo(0);
		assertThat(body.durationMs()).isGreaterThanOrEqualTo(0);
	}

	@DisplayName("비운영 또는 loadtest 프로필이면 GC 컨트롤러가 생성되고, prod 단독이면 생성되지 않는다")
	@ParameterizedTest(name = "profiles={0} → 생성={1}")
	@CsvSource({
		"local, true",
		"test, true",
		"'prod,loadtest', true",
		"prod, false"
	})
	void bean_exists_only_for_non_prod_or_loadtest(String profiles, boolean expected) {
		// given
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withUserConfiguration(GcTriggerController.class)
				.withPropertyValues("spring.profiles.active=" + profiles);

		// when & then
		runner.run(context -> assertThat(context.containsBean("gcTriggerController")).isEqualTo(expected));
	}
}
