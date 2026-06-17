package com.triagain.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 비동기 실행 설정 — 알림 fan-out 전용 스레드 풀 */
@Configuration
@EnableAsync
public class AsyncConfig {

	/** 알림 비동기 발송 전용 풀 — 요청 스레드 분리, 작은 바운드 (소규모 트래픽) */
	@Bean(name = "notificationExecutor")
	public Executor notificationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("noti-async-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}
}
