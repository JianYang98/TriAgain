package com.triagain.acceptance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.triagain.acceptance.adapter.CapturingSsePort;

/** 쿠컴버 전용 SSE 포트 빈 — CapturingSsePort를 @Primary로 등록해 실제 SseEmitterAdapter를 대체한다(⓪ 격리) */
@TestConfiguration
public class CucumberTestConfig {

	@Bean
	@Primary
	public CapturingSsePort capturingSsePort() {
		return new CapturingSsePort();
	}
}
