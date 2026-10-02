package com.triagain.crew.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.crew.application.CrewJoinPendingWorker;
import com.triagain.crew.application.CrewJoinPersistenceService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Port 예외 대신 실제 Redis 명령 응답을 TCP에서 유실시켜 클라이언트 재전송을 검증한다. */
@Tag("e2e")
class CrewJoinTransportFailureIntegrationTest {

	@ParameterizedTest
	@ValueSource(strings = {"BLMOVE", "LREM"})
	@DisplayName("claim 또는 ACK 응답 전에 TCP가 끊기면 재전송 없이 worker가 멈춘다")
	void replyLost_stopsWithoutTransparentReplay(String command) throws Exception {
		// Given — 생산·관측 연결은 프록시를 우회하고 worker에만 실제 응답 유실을 주입한다.
		try (RedisReplyDropProxy proxy = new RedisReplyDropProxy()) {
			LettuceConnectionFactory observer = factory(RedisTestContainer.getHost(), RedisTestContainer.getPort());
			LettuceConnectionFactory upstream = factory("localhost", proxy.port());
			var properties = new CrewJoinRedisProperties("wire", UUID.randomUUID().toString());
			var queue = new CrewJoinRedisConfiguration().crewJoinWorkQueuePort(
				upstream, properties);
			var persistence = mock(CrewJoinPersistenceService.class);
			when(persistence.persist(any(), any(), any())).thenReturn(1);
			var worker = new CrewJoinPendingWorker(queue, persistence, new ObjectMapper(), "wire", properties.runId());
			StringRedisTemplate redis = new StringRedisTemplate(observer);
			var logger = (Logger)LoggerFactory.getLogger(CrewJoinPendingWorker.class);
			var logs = new ListAppender<ILoggingEvent>();
			logs.start();
			logger.addAppender(logs);
			try {
				redis.opsForList().leftPushAll(properties.pendingKey(), payload("A"), payload("B"));
				proxy.dropNext(command);
				// When — 서버가 명령을 실행한 뒤 응답을 버리고 TCP를 닫는다.
				worker.start();
				await().atMost(Duration.ofSeconds(3)).until(() -> proxy.dropped() == 1);
				await().atMost(Duration.ofSeconds(4)).until(() -> !worker.isRunning()
					|| proxy.claims() > 1 || proxy.acknowledgements() > 1);
				// Then — 두 번째 작업을 몰래 가져오거나 이미 보낸 ACK를 재전송하지 않는다.
				assertThat(proxy.failure()).isNull();
				assertThat(proxy.claims()).isEqualTo(1);
				assertThat(proxy.acknowledgements()).isEqualTo(command.equals("LREM") ? 1 : 0);
				assertThat(redis.opsForList().range(properties.pendingKey(), 0, -1)).containsExactly(payload("B"));
				assertThat(redis.opsForList().range(properties.processingKey(), 0, -1))
					.containsExactlyElementsOf(command.equals("BLMOVE") ? List.of(payload("A")) : List.of());
				await().atMost(Duration.ofSeconds(2)).until(() -> !worker.isRunning());
				verify(persistence, times(command.equals("LREM") ? 1 : 0)).persist(any(), any(), any());
				assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains(
					"CREW_JOIN_WORKER_STOPPED", "namespace=wire", "runId=" + properties.runId(),
					"stage=" + (command.equals("BLMOVE") ? "claim" : "ACK")));
			} finally {
				worker.stop();
				logger.detachAppender(logs);
				queue.close();
				redis.delete(List.of(properties.pendingKey(), properties.processingKey()));
				upstream.destroy();
				observer.destroy();
			}
		}
	}

	private static LettuceConnectionFactory factory(String host, int port) {
		var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build();
		var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), client);
		factory.afterPropertiesSet();
		return factory;
	}

	private static String payload(String user) {
		return "{\"crewId\":\"crew\",\"userId\":\"" + user
			+ "\",\"confirmedAt\":\"2026-01-02T03:04:05.678+09:00\"}";
	}
}
