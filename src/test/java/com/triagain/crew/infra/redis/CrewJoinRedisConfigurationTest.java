package com.triagain.crew.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * 전략별 Redis wiring — DB 전략은 Redis 없이 기동·Redis health 미등록, REDIS_ASYNC는 PING 실패 시 기동 실패.
 * 실제 HTTP /actuator/health는 ActuatorHealthWithoutRedisE2eTest가 본다.
 */
@Tag("e2e")
class CrewJoinRedisConfigurationTest {

	/** 아무것도 listen하지 않는 포트 — Redis 부재 */
	private static final String CLOSED_PORT = "spring.data.redis.port=1";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(
			RedisAutoConfiguration.class,
			HealthContributorAutoConfiguration.class,
			RedisHealthContributorAutoConfiguration.class,
			RedisReactiveHealthContributorAutoConfiguration.class))
		.withUserConfiguration(PropertiesConfig.class, CrewJoinRedisConfiguration.class)
		.withPropertyValues(
			"spring.data.redis.connect-timeout=1s",
			"spring.data.redis.timeout=2s",
			"management.health.redis.enabled=false");

	@Test
	@DisplayName("DB 전략은 Redis가 없어도 기동하고 기동 게이트·Redis health indicator가 없다")
	void dbStrategy_startsWithoutRedis() {
		runner
			.withPropertyValues("triagain.crew.lock-strategy=CONDITIONAL")
			.withPropertyValues("spring.data.redis.host=localhost", CLOSED_PORT)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(CrewJoinRedisStartupCheck.class);
				assertThat(context).doesNotHaveBean("redisHealthIndicator");
				assertThat(context).doesNotHaveBean("redisHealthContributor");
			});
	}

	@Test
	@DisplayName("대조군 — management.health.redis.enabled를 켜면 DB 전략에도 Boot Redis health indicator가 등록된다")
	void control_bootRedisHealthIsRegisteredWhenEnabled() {
		runner.withPropertyValues("triagain.crew.lock-strategy=CONDITIONAL", CLOSED_PORT,
				"management.health.redis.enabled=true")
			// reactor-core가 classpath에 있어 Boot는 reactive indicator를 redisHealthContributor 이름으로 등록한다(실측)
			.run(context -> assertThat(context).hasBean("redisHealthContributor"));
	}

	@Test
	@DisplayName("REDIS_ASYNC에서 Redis에 연결할 수 없으면 기동이 실패한다")
	void redisAsync_failsStartupWhenRedisUnavailable() {
		runner.withPropertyValues(redisAsync("run-a"))
			.withPropertyValues("spring.data.redis.host=localhost", CLOSED_PORT)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("CONNECTION")
					.hasRootCauseInstanceOf(java.net.ConnectException.class)
					.cause().isInstanceOf(RedisConnectionFailureException.class);
			});
	}

	@Test
	@DisplayName("REDIS_ASYNC에서 run-id가 비어 있으면 기본값 없이 기동이 실패한다")
	void redisAsync_failsStartupWhenRunIdBlank() {
		runner.withPropertyValues(redisAsync("")).withPropertyValues(redisAddress())
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasMessageContaining("triagain.crew.redis.run-id");
			});
	}

	@Test
	@DisplayName("REDIS_ASYNC에서 Redis가 정상이면 기동하고 Redis health가 UP이다")
	void redisAsync_startsWhenRedisAvailable() {
		runner.withPropertyValues(redisAsync("run-a")).withPropertyValues(redisAddress())
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(CrewJoinRedisStartupCheck.class);
				HealthIndicator health = context.getBean("redisHealthIndicator", HealthIndicator.class);
				assertThat(health.health().getStatus()).isEqualTo(Status.UP);
			});
	}

	private static String[] redisAsync(String runId) {
		return new String[] {
			"triagain.crew.lock-strategy=REDIS_ASYNC",
			"triagain.crew.redis.namespace=test",
			"triagain.crew.redis.run-id=" + runId};
	}

	private static String[] redisAddress() {
		return new String[] {
			"spring.data.redis.host=" + RedisTestContainer.getHost(),
			"spring.data.redis.port=" + RedisTestContainer.getPort()};
	}

	@Configuration
	@EnableConfigurationProperties(CrewJoinRedisProperties.class)
	static class PropertiesConfig {
	}
}
