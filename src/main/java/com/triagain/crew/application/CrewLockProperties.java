package com.triagain.crew.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** 크루 동시성 락 전략 설정 — yml로 PESSIMISTIC/OPTIMISTIC/CONDITIONAL 전환 */
@Getter
@Setter
@ConfigurationProperties(prefix = "triagain.crew")
public class CrewLockProperties {

	private LockStrategy lockStrategy = LockStrategy.OPTIMISTIC;

	private int maxRetry = 3;

	public enum LockStrategy { PESSIMISTIC, OPTIMISTIC, CONDITIONAL }

	public boolean isPessimistic() {
		return lockStrategy == LockStrategy.PESSIMISTIC;
	}

	/** 조건부 원자적 UPDATE 전략 여부 */
	public boolean isConditional() {
		return lockStrategy == LockStrategy.CONDITIONAL;
	}
}
