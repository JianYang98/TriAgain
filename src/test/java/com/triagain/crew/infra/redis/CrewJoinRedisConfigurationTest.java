package com.triagain.crew.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.crew.application.CrewJoinPendingWorker;
import com.triagain.crew.port.out.CrewRepositoryPort;

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
		.withBean(ObjectMapper.class, ObjectMapper::new)
		.withBean(CrewRepositoryPort.class, () -> mock(CrewRepositoryPort.class))
		.withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class))
		.withPropertyValues(
			"spring.data.redis.connect-timeout=1s",
			"spring.data.redis.timeout=2s",
			"management.health.redis.enabled=false");

	@ParameterizedTest
	@ValueSource(strings = {"CONDITIONAL", "PESSIMISTIC", "OPTIMISTIC"})
	@DisplayName("DB 전략은 Redis가 없어도 기동하고 기동 게이트·Redis health indicator가 없다")
	void dbStrategy_startsWithoutRedis(String strategy) {
		runner
			.withPropertyValues("triagain.crew.lock-strategy=" + strategy)
			.withPropertyValues("spring.data.redis.host=localhost", CLOSED_PORT)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(CrewJoinRedisStartupCheck.class);
				assertThat(context).doesNotHaveBean(CrewJoinPendingWorker.class);
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
				assertThat(context).hasSingleBean(CrewJoinPendingWorker.class);
				assertThat(context).hasSingleBean(RedisConnectionFactory.class);
				var producer = context.getBean(LettuceConnectionFactory.class);
				assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory()).isSameAs(producer);
				assertThat(producer.getClientConfiguration().getClientOptions().orElseThrow().isAutoReconnect())
					.isTrue();
				assertThat(context.getBean(CrewJoinPendingWorker.class).isRunning()).isTrue();
				HealthIndicator health = context.getBean("redisHealthIndicator", HealthIndicator.class);
				assertThat(health.health().getStatus()).isEqualTo(Status.UP);
			});
	}

	@Test
	@DisplayName("REDIS_ASYNC를 relaxed 표기(redis-async)로 써도 enum 바인딩과 같게 기동 게이트·Redis health가 등록된다")
	void redisAsync_relaxedSpellingRegistersStartupCheckAndHealth() {
		// Given — CrewLockProperties는 enum을 lenient 바인딩하므로 redis-async도 JoinCrewService를 Redis 경로로 보낸다
		runner.withPropertyValues(
				"triagain.crew.lock-strategy=redis-async",
				"triagain.crew.redis.namespace=test",
				"triagain.crew.redis.run-id=run-a")
			.withPropertyValues(redisAddress())
			// When / Then — 설정도 같은 판단으로 켜져야 한다
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(CrewJoinRedisStartupCheck.class);
				assertThat(context).hasSingleBean(CrewJoinPendingWorker.class);
				assertThat(context).hasSingleBean(RedisConnectionFactory.class);
				var producer = context.getBean(LettuceConnectionFactory.class);
				assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory()).isSameAs(producer);
				assertThat(producer.getClientConfiguration().getClientOptions().orElseThrow().isAutoReconnect())
					.isTrue();
				assertThat(context.getBean(CrewJoinPendingWorker.class).isRunning()).isTrue();
				assertThat(context).hasBean("redisHealthIndicator");
			});
	}

	@Test
	@DisplayName("DB 전략을 relaxed 표기(pessimistic)로 써도 기동 게이트·Redis health가 없다")
	void dbStrategy_relaxedSpellingDoesNotRegisterRedisWiring() {
		runner.withPropertyValues("triagain.crew.lock-strategy=pessimistic", CLOSED_PORT)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(CrewJoinRedisStartupCheck.class);
				assertThat(context).doesNotHaveBean(CrewJoinPendingWorker.class);
				assertThat(context).doesNotHaveBean("redisHealthIndicator");
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
