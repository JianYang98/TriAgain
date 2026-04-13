package com.triagain.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시스템 Clock Bean — 테스트에서 고정 시각 주입을 위해 Bean으로 등록 */
@Configuration
public class ClockConfig {

	/** 기본 시스템 Clock 제공 — 테스트에서 Clock.fixed()로 교체 가능 */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}
}
