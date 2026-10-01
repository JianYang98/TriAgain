package com.triagain.crew.infra.redis;

import java.time.Duration;

import org.springframework.boot.actuate.data.redis.RedisHealthIndicator;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.crew.application.CrewJoinPendingWorker;
import com.triagain.crew.application.CrewJoinPersistenceService;
import com.triagain.crew.application.CrewLockProperties.LockStrategy;
import com.triagain.crew.port.out.CrewJoinWorkQueuePort;
import com.triagain.crew.port.out.CrewRepositoryPort;

/**
 * REDIS_ASYNC 전용 wiring — DB 세 전략에서는 이 설정 전체가 없다.
 * 공통 yml이 Boot 기본 Redis health를 끄므로(DB 전략 health가 Redis 부재로 503이 되지 않게),
 * Redis 전략에서만 같은 이름의 health indicator를 직접 등록한다.
 */
@Configuration
@Conditional(CrewJoinRedisConfiguration.OnRedisAsyncStrategy.class)
public class CrewJoinRedisConfiguration {

	/** Redis 전략의 health 반영 — 기동 후 Redis 장애 시 /actuator/health DOWN */
	@Bean
	RedisHealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
		return new RedisHealthIndicator(connectionFactory);
	}

	/** 기동 게이트 — 웹 서버 포트 오픈 전 PING */
	@Bean
	CrewJoinRedisStartupCheck crewJoinRedisStartupCheck(
		CrewJoinRedisProperties properties, RedisConnectionFactory connectionFactory) {
		return new CrewJoinRedisStartupCheck(properties, connectionFactory);
	}

	/** worker 전용 queue — 유한 block 대기와 command timeout의 여유를 검증 */
	@Bean
	CrewJoinWorkQueuePort crewJoinWorkQueuePort(StringRedisTemplate template,
		CrewJoinRedisProperties properties, RedisProperties redisProperties) {
		Duration commandTimeout = redisProperties.getTimeout();
		if (commandTimeout == null || commandTimeout.compareTo(CrewJoinPendingWorker.BLOCK_TIMEOUT) <= 0) {
			throw new IllegalStateException("Redis command timeout must exceed worker block timeout (1s)");
		}
		return new CrewJoinWorkQueueAdapter(template, properties);
	}

	/** DB commit 경계를 소유하는 서비스 — Redis ACK는 포함하지 않음 */
	@Bean
	CrewJoinPersistenceService crewJoinPersistenceService(CrewRepositoryPort repository,
		TransactionTemplate transactionTemplate) {
		return new CrewJoinPersistenceService(repository, transactionTemplate);
	}

	/** REDIS_ASYNC에서만 단일 worker 등록 — processing 잔존 여부는 조회하지 않음 */
	@Bean
	CrewJoinPendingWorker crewJoinPendingWorker(CrewJoinWorkQueuePort queue,
		CrewJoinPersistenceService persistence, ObjectMapper mapper, CrewJoinRedisProperties properties) {
		return new CrewJoinPendingWorker(queue, persistence, mapper, properties.namespace(), properties.runId());
	}

	/**
	 * CrewLockProperties와 같은 enum 바인딩으로 판정 — 문자열 비교(@ConditionalOnProperty)는
	 * redis-async 같은 relaxed 표기를 놓쳐 가입은 Redis 경로인데 기동 게이트·health가 빠진다.
	 */
	static class OnRedisAsyncStrategy implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return Binder.get(context.getEnvironment())
				.bind("triagain.crew.lock-strategy", LockStrategy.class)
				.map(LockStrategy.REDIS_ASYNC::equals)
				.orElse(false);
		}
	}
}
