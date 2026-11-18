package com.triagain.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulerPoolDefaultTest {

	@DisplayName("각 프로필에서 스케줄러 풀 설정이 없으면 Spring Boot 기본 크기 1을 사용한다")
	@ParameterizedTest
	@ValueSource(strings = {"local", "dev", "prod", "local,loadtest", "prod,loadtest"})
	void scheduler_pool_uses_boot_default_of_one(String profiles) {
		// Given — 실제 application.yml과 지정 프로필 설정을 불러온다.
		new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
				.withUserConfiguration(SchedulingEnabledConfiguration.class)
				.withPropertyValues("spring.profiles.active=" + profiles)
				.run(context -> {
					// When — Spring Boot가 바인딩한 풀 설정과 실제 scheduler executor를 확인한다.
					assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
					assertThat(context.getBean(TaskSchedulingProperties.class).getPool().getSize())
							.isEqualTo(1);
					// Then — 실행기 생성 전 worker 수가 아니라 예약 스레드 core 설정값이 1이어야 한다.
					assertThat(context.getBean(ThreadPoolTaskScheduler.class)
							.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
				});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	static class SchedulingEnabledConfiguration {
	}
}
