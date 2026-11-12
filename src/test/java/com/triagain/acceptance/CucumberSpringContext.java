package com.triagain.acceptance;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.cucumber.spring.CucumberContextConfiguration;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(CucumberTestConfig.class)
public class CucumberSpringContext {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", TestContainers::getJdbcUrl);
		registry.add("spring.datasource.username", TestContainers::getUsername);
		registry.add("spring.datasource.password", TestContainers::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		// 취소 컷오프 해제 — 기본 5분이면 하루의 마지막 5분(23:55~24:00)에 실행될 때 취소 시나리오가
		// 벽시계 때문에 결정적으로 깨진다(crew.deadlineTime 상한이 23:59:59라 어떤 앵커로도 못 벗어난다).
		// 컷오프 자체를 검증하는 인수 시나리오는 없으므로 0으로 내려 시각 의존을 제거한다.
		// ⚠️ 컷오프를 인수로 검증하려는 시나리오가 생기면 이 줄을 되돌리고 그 시나리오에서 값을 지정한다.
		registry.add("triagain.verification.cancel-cutoff-minutes", () -> "0");
	}
}
