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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

	@ParameterizedTest(name = "{0}")
	@CsvSource({"partial-move,1,A,'D,B'", "last-item-move,2,'','D,B,A'", "drain-nil,3,'','D,B,A'"})
	@DisplayName("P4-T7 서버가 실행한 recovery의 응답이 유실되면 결과 불명으로 worker를 시작하지 않는다")
	void recoveryReplyLost_failsAsUnknownWithoutStartingWorker(String scenario, int dropAt, String processing,
		String pending) throws Exception {
		try (RedisReplyDropProxy proxy = new RedisReplyDropProxy()) {
			// Given — processing [B,A], pending [D]. dropAt번째 LMOVE 응답만 유실시킨다.
			proxy.dropRecoveryReply(dropAt);
			Recovery run = recover(factory("localhost", proxy.port()));
			// Then — 앱은 정상 nil을 받지 못했으므로 실제 이동 여부와 무관하게 실패다.
			assertThat(proxy.failure()).isNull();
			assertThat(proxy.dropped()).isEqualTo(1);
			assertThat(proxy.recoveries()).isEqualTo(dropAt);
			assertThat(proxy.claims()).isZero();
			assertThat(run.started()).isFalse();
			assertThat(run.failure()).contains("namespace=wire", "confirmedMoves=" + (dropAt - 1),
				"outcomeUnknown=true", "workerStarted=false", "retryInProcess=false",
				"admissionBlockedByRecovery=false");
			// observer만 실제 상태를 안다 — 마지막 이동·nil 유실이면 processing은 이미 비어 있다.
			assertThat(run.processing()).isEqualTo(users(processing));
			assertThat(run.pending()).isEqualTo(users(pending));
		}
	}

	@Test
	@DisplayName("P4-T7 대조군 — recovery 연결 획득이 전송 전에 실패하면 확정 미이동이고 worker를 시작하지 않는다")
	void recoveryConnectFailure_beforeSend_leavesQueueUnchanged() throws Exception {
		// When — 아무것도 listen하지 않는 포트
		Recovery run = recover(factory("localhost", 1));
		// Then
		assertThat(run.started()).isFalse();
		assertThat(run.failure()).contains("namespace=wire", "confirmedMoves=0", "outcomeUnknown=false",
			"workerStarted=false");
		assertThat(run.processing()).containsExactly("B", "A");
		assertThat(run.pending()).containsExactly("D");
	}

	@Test
	@DisplayName("P4-T12 LMOVE 응답 유실 후 TCP가 닫혀도 재연결·재전송하지 않아 두 번째 항목은 processing에 남는다")
	void recoveryReplyLost_noTransparentReplay() throws Exception {
		try (RedisReplyDropProxy proxy = new RedisReplyDropProxy()) {
			// Given — source 2건, 첫 LMOVE 응답 유실. 프록시는 재연결을 계속 수락한다.
			proxy.dropRecoveryReply(1);
			Recovery run = recover(factory("localhost", proxy.port()));
			// Then — command timeout(2s)을 넘기는 관찰 구간에도 wire LMOVE는 1회, claim 0회
			await().during(Duration.ofMillis(2500)).atMost(Duration.ofSeconds(4)).untilAsserted(() -> {
				assertThat(proxy.recoveries()).as("wire LMOVE 횟수").isEqualTo(1);
				assertThat(proxy.claims()).as("wire BLMOVE 횟수").isZero();
			});
			assertThat(proxy.failure()).isNull();
			assertThat(run.started()).isFalse();
			assertThat(run.processing()).containsExactly("A");
			assertThat(run.pending()).containsExactly("D", "B");
			assertThat(run.failure()).contains("namespace=wire", "confirmedMoves=0", "outcomeUnknown=true");
		}
	}

	private Recovery recover(LettuceConnectionFactory upstream) throws Exception {
		LettuceConnectionFactory observer = factory(RedisTestContainer.getHost(), RedisTestContainer.getPort());
		var properties = new CrewJoinRedisProperties("wire", UUID.randomUUID().toString());
		var queue = new CrewJoinRedisConfiguration().crewJoinWorkQueuePort(upstream, properties);
		var persistence = mock(CrewJoinPersistenceService.class);
		var worker = new CrewJoinPendingWorker(queue, persistence, new ObjectMapper(), "wire", properties.runId());
		StringRedisTemplate redis = new StringRedisTemplate(observer);
		var logger = (Logger)LoggerFactory.getLogger(CrewJoinPendingWorker.class);
		var logs = new ListAppender<ILoggingEvent>();
		logs.start();
		logger.addAppender(logs);
		try {
			redis.opsForList().rightPushAll(properties.processingKey(), payload("B"), payload("A"));
			redis.opsForList().rightPush(properties.pendingKey(), payload("D"));
			// When — recovery는 start() 안에서 동기로 끝난다.
			worker.start();
			verifyNoInteractions(persistence);
			String failure = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter(message -> message.startsWith("CREW_JOIN_RECOVERY_FAILED")).findFirst().orElse("");
			return new Recovery(worker.isRunning(), failure, users(redis, properties.processingKey()),
				users(redis, properties.pendingKey()));
		} finally {
			worker.stop();
			logger.detachAppender(logs);
			queue.close();
			redis.delete(List.of(properties.pendingKey(), properties.processingKey()));
			upstream.destroy();
			observer.destroy();
		}
	}

	private static List<String> users(StringRedisTemplate redis, String key) {
		return redis.opsForList().range(key, 0, -1).stream()
			.map(raw -> raw.replaceAll(".*\"userId\":\"([^\"]+)\".*", "$1")).toList();
	}

	private static List<String> users(String csv) {
		return csv.isEmpty() ? List.of() : List.of(csv.split(","));
	}

	private record Recovery(boolean started, String failure, List<String> processing, List<String> pending) {
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
