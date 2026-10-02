package com.triagain.crew.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslVerifyMode;

class CrewJoinWorkerRedisConnectionTest {

	@Test
	@DisplayName("worker 설정과 종료를 분리하면 producer의 접속 설정과 재연결은 유지된다")
	void workerIsolation_preservesProducerConfigurationAndLifecycle() {
		// Given — 주소·DB·인증·TLS·timeout은 기존 factory가 해석한 값을 계승한다.
		var standalone = new RedisStandaloneConfiguration("localhost", 6379);
		standalone.setDatabase(3);
		standalone.setUsername("worker-test");
		standalone.setPassword("test-only");
		var options = ClientOptions.builder()
			.socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build()).build();
		var client = LettuceClientConfiguration.builder().clientOptions(options).commandTimeout(Duration.ofSeconds(2))
			.clientName("experiment").useSsl().verifyPeer(SslVerifyMode.FULL).and().build();
		var producer = new LettuceConnectionFactory(standalone, client);
		producer.afterPropertiesSet();
		try {
			assertWorkerIsolation(producer);
		} finally {
			producer.destroy();
		}
	}

	private void assertWorkerIsolation(LettuceConnectionFactory producer) {
		// When — Boot 자동 설정을 대체하지 않는 private factory를 만들고 종료한다.
		var connection = new CrewJoinWorkerRedisConnection(producer);
		var worker = (LettuceConnectionFactory)connection.template().getRequiredConnectionFactory();
		try {
			assertSettings(producer, worker);
		} finally {
			connection.close();
		}
		// Then — worker 종료가 producer factory를 종료시키지 않는다.
		assertThat(worker.isRunning()).isFalse();
		assertThat(producer.isRunning()).isTrue();
		assertThat(producer.getClientConfiguration().getClientOptions().orElseThrow().isAutoReconnect()).isTrue();
	}

	private void assertSettings(LettuceConnectionFactory producer, LettuceConnectionFactory worker) {
		var config = worker.getClientConfiguration();
		var options = config.getClientOptions().orElseThrow();
		assertThat(worker).isNotSameAs(producer);
		assertThat(worker.getStandaloneConfiguration()).isEqualTo(producer.getStandaloneConfiguration());
		assertThat(config.getCommandTimeout()).isEqualTo(Duration.ofSeconds(2));
		assertThat(config.getClientName()).contains("experiment");
		assertThat(config.isUseSsl()).isTrue();
		assertThat(config.getVerifyMode()).isEqualTo(SslVerifyMode.FULL);
		assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
		assertThat(options.isAutoReconnect()).isFalse();
		assertThat(options.getDisconnectedBehavior()).isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
	}
}
