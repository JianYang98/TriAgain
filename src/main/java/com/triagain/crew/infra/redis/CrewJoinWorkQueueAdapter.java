package com.triagain.crew.infra.redis;

import java.time.Duration;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.triagain.crew.port.out.CrewJoinWorkQueuePort;

public class CrewJoinWorkQueueAdapter implements CrewJoinWorkQueuePort, AutoCloseable {

	private final StringRedisTemplate redisTemplate;
	private final CrewJoinRedisProperties properties;
	private final CrewJoinWorkerRedisConnection connection;

	/** 기존 standalone 접속 설정을 계승하되 claim·ACK의 자동 재전송은 worker에서만 차단 */
	public CrewJoinWorkQueueAdapter(LettuceConnectionFactory producer, CrewJoinRedisProperties properties) {
		this.connection = new CrewJoinWorkerRedisConnection(producer);
		this.redisTemplate = connection.template();
		this.properties = properties;
	}

	/** 소비 루프 종료 후 worker 전용 연결 해제 */
	@Override
	public void close() {
		connection.close();
	}

	/** 유한 BLMOVE로 FIFO 작업을 원자 이동하고 수신 원문 반환
	 *  BLMOVE이 되면! 응답 JSON을 받고 , 대기시간까지 응답 없으면 null 리턴
	 * */
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

	/**
	 * timeout 없는 LMOVE processing pending LEFT RIGHT — 예외를 nil로 바꾸지 않는다.
	 * 연결 획득 실패만 DataAccessResourceFailureException(확정 미전송)이고, 획득 뒤 실패는 결과 불명으로 바꿔 던진다.
	 */
	@Override
	public String recoverOneRaw() {
		redisTemplate.getRequiredConnectionFactory().getConnection().close();
		try {
			return redisTemplate.opsForList().move(properties.processingKey(), Direction.LEFT,
				properties.pendingKey(), Direction.RIGHT);
		} catch (DataAccessResourceFailureException exception) {
			throw new RedisSystemException("LMOVE outcome unknown", exception);
		}
	}
}
