package com.triagain.support.infra;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 리액션 실 DB 계층(레인 2: 전용 컨테이너 + Flyway ON, ddl-auto=validate)의 공용 베이스.
 * {@code ReactionIntegrationTest}·{@code ReactionApiTest}·{@code ReactionUniqueConstraintsIntegrationTest}가
 * 이 컨테이너 1개를 공유한다(step1-biz-logic.md §7-0). 선례는
 * {@code VerificationUniqueConstraintsIntegrationTest}·{@code HabitUniqueConstraintsIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
@Testcontainers
public abstract class ReactionIntegrationTestBase {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("triagain_reaction")
			.withUsername("reactionuser")
			.withPassword("reactionpass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}
}
