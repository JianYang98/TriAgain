package com.triagain.crew.infra.redis;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 가입 실험의 key 식별 설정 — namespace는 환경/소유자, runId는 독립 실험 구분 */
@ConfigurationProperties(prefix = "triagain.crew.redis")
public record CrewJoinRedisProperties(String namespace, String runId) {

	private static final Pattern KEY_PART = Pattern.compile("[A-Za-z0-9_-]+");

	/** 필수값·형식 검증 — REDIS_ASYNC 기동 시에만 호출. DB 전략은 이 값을 쓰지 않으므로 검증하지 않는다 */
	public void validate() {
		requireKeyPart("triagain.crew.redis.namespace", namespace);
		requireKeyPart("triagain.crew.redis.run-id", runId);
	}

	private static void requireKeyPart(String name, String value) {
		// 빈 값에 기본값을 대입하지 않는다 — 다른 실험의 key와 합쳐지는 것을 막기 위해 기동을 멈춘다
		if (value == null || !KEY_PART.matcher(value).matches()) {
			throw new IllegalStateException(name + " must match [A-Za-z0-9_-]+ (actual: " + value + ")");
		}
	}
}
