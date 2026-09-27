package com.triagain.crew.infra.redis;

import org.springframework.boot.actuate.data.redis.RedisHealthIndicator;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.triagain.crew.application.CrewLockProperties.LockStrategy;

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
