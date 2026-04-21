package com.triagain.crew.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** 크루 동시성 락 전략 설정 — yml로 PESSIMISTIC/OPTIMISTIC 전환 */
@Getter
@Setter
@ConfigurationProperties(prefix = "triagain.crew")
public class CrewLockProperties {

	private LockStrategy lockStrategy;

	private Integer maxRetry;

	public enum LockStrategy { PESSIMISTIC, OPTIMISTIC }

	public boolean isPessimistic() {
		return lockStrategy == LockStrategy.PESSIMISTIC;
	}
}
