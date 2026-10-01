package com.triagain.crew.infra.redis;

import java.time.Duration;

import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.triagain.crew.port.out.CrewJoinWorkQueuePort;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CrewJoinWorkQueueAdapter implements CrewJoinWorkQueuePort {

	private final StringRedisTemplate redisTemplate;
	private final CrewJoinRedisProperties properties;

	/** 유한 BLMOVE로 FIFO 작업을 원자 이동하고 수신 원문 반환 */
	@Override
	public String claimRaw(Duration timeout) {
		return redisTemplate.opsForList().move(properties.pendingKey(), Direction.RIGHT,
			properties.processingKey(), Direction.LEFT, timeout);
	}

	/** 재직렬화 없이 claim 원문 한 건만 processing에서 제거 */
	@Override
	public long ackRaw(String raw) {
		Long removed = redisTemplate.opsForList().remove(properties.processingKey(), 1, raw);
		if (removed == null) {
			throw new IllegalStateException("Redis ACK result unknown");
		}
		return removed;
	}
}
