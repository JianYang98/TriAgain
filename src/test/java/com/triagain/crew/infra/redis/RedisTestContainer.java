package com.triagain.crew.infra.redis;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** Redis 테스트 컨테이너 싱글턴 — @Container를 쓰지 않는다(클래스 단위 stop 방지). JVM 종료 시 Ryuk이 정리 */
public final class RedisTestContainer {

	/** docker-compose.yml의 redis 이미지와 같은 tag로 고정 */
	public static final String IMAGE = "redis:7.4.11-alpine";

	private static final GenericContainer<?> REDIS;

	static {
		REDIS = new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(6379);
		REDIS.start();
	}

	private RedisTestContainer() {
	}

	public static String getHost() {
		return REDIS.getHost();
	}

	public static int getPort() {
		return REDIS.getMappedPort(6379);
	}

	/** 준비 스크립트를 docker exec로 이 컨테이너에 붙일 때 쓴다 */
	public static String getContainerId() {
		return REDIS.getContainerId();
	}
}
