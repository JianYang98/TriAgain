package com.triagain.common.config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/** 비동기 실행 설정 — 알림 fan-out 전용 스레드 풀 + @Async void 미처리 예외 핸들러 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

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

	/**
	 * 기본 executor는 null 반환 — @Async("notificationExecutor") 명시 사용이라 기본 풀 불필요
	 */
	@Override
	public Executor getAsyncExecutor() {
		return null;
	}

	/**
	 * @Async void 메서드의 미처리 예외 핸들러 — FCM-401 등 관측 공백 방지
	 */
	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return (Throwable ex, Method method, Object... params) ->
				log.error("@Async 미처리 예외 [{}] args={}: {}",
						method.getName(), Arrays.toString(params), ex.getMessage(), ex);
	}
}
