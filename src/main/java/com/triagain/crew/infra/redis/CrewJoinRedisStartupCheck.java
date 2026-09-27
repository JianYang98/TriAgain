package com.triagain.crew.infra.redis;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REDIS_ASYNC 기동 게이트 — 설정 검증 + Redis PING, 실패 시 예외로 기동을 중단한다.
 * 싱글턴 초기화 직후에 돌기 때문에 웹 서버가 포트를 열기(finishRefresh) 전에 끝난다.
 * ApplicationRunner/ReadyEvent는 포트가 열린 뒤라 쓰지 않는다. seed·reset·worker 시작 없음.
 */
@Slf4j
@RequiredArgsConstructor
public class CrewJoinRedisStartupCheck implements SmartInitializingSingleton {

	private final CrewJoinRedisProperties properties;
	private final RedisConnectionFactory connectionFactory;

	/** 모든 싱글턴 생성 직후 호출 — 연결 확인 실패는 ApplicationContext refresh 실패로 전파 */
	@Override
	public void afterSingletonsInstantiated() {
		properties.validate();
		String pong;
		try (RedisConnection connection = connectionFactory.getConnection()) {
			pong = connection.ping();
		} catch (DataAccessException e) {
			throw new IllegalStateException("CONNECTION: REDIS_ASYNC startup Redis PING failed", e);
		}
		if (!"PONG".equals(pong)) {
			throw new IllegalStateException("CONNECTION: unexpected Redis PING reply: " + pong);
		}
		log.info("[CrewJoinRedis] startup PING ok (namespace={}, runId={})",
			properties.namespace(), properties.runId());
	}
}
