package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagain.crew.port.out.CrewJoinWorkQueuePort;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class CrewJoinPendingWorkerTest {

	private static final String RAW = "{\"crewId\":\"crew\",\"userId\":\"user\","
		+ "\"confirmedAt\":\"2026-01-02T03:04:05.678+09:00\"}";
	private final CrewJoinWorkQueuePort queue = mock(CrewJoinWorkQueuePort.class);
	private final CrewJoinPersistenceService persistence = mock(CrewJoinPersistenceService.class);

	@Test
	@DisplayName("claim이 실패하면 상태를 추정하거나 재시도하지 않고 중단한다")
	void claimFailure_stopsWithoutGuessingQueueOrRetrying() {
		// Given — 실패 또는 종료 경계를 제어한다.
		when(queue.claimRaw(any())).thenThrow(new IllegalStateException("timeout"));
		// When / Then — 실제 소비 루프의 중단과 오류 로그를 확인한다.
		assertStoppedWithLog("claim", RAW);
		// Then — 추가 claim·ACK가 실행되지 않는다.
		verify(queue, times(1)).claimRaw(CrewJoinPendingWorker.BLOCK_TIMEOUT);
		verifyNoMoreInteractions(queue);
		verifyNoInteractions(persistence);
	}

	@ParameterizedTest
	@MethodSource("malformedPayloads")
	@DisplayName("payload가 잘못되면 DB 반영과 ACK 없이 중단한다")
	void malformedPayload_noDbOrAckAndStops(String raw) {
		// Given — 실패 또는 종료 경계를 제어한다.
		when(queue.claimRaw(any())).thenReturn(raw, RAW);
		// When / Then — 실제 소비 루프의 중단과 오류 로그를 확인한다.
		assertStoppedWithLog("parse", raw);
		// Then — 추가 claim·ACK가 실행되지 않는다.
		verify(queue, times(1)).claimRaw(any());
		verifyNoMoreInteractions(queue);
		verifyNoInteractions(persistence);
	}

	@Test
	@DisplayName("DB 반영이 실패하면 ACK 없이 중단한다")
	void dbFailure_noAckAndStops() {
		// Given — 실패 또는 종료 경계를 제어한다.
		when(queue.claimRaw(any())).thenReturn(RAW);
		when(persistence.persist(any(), any(), any())).thenThrow(new IllegalStateException("DB unavailable"));
		// When / Then — 실제 소비 루프의 중단과 오류 로그를 확인한다.
		assertStoppedWithLog("DB", RAW);
		// Then — 추가 claim·ACK가 실행되지 않는다.
		verify(queue, times(1)).claimRaw(any());
		verify(queue, never()).ackRaw(any());
		verify(persistence, times(1)).persist("crew", "user", LocalDateTime.of(2026, 1, 2, 3, 4, 5, 678000000));
	}

	@Test
	@DisplayName("ACK가 실패하면 DB 반환 후 소비를 중단한다")
	void ackFailure_stopsAfterDbReturn() {
		// Given — 실패 또는 종료 경계를 제어한다.
		when(queue.claimRaw(any())).thenReturn(RAW);
		when(queue.ackRaw(RAW)).thenReturn(0L);
		// When / Then — 실제 소비 루프의 중단과 오류 로그를 확인한다.
		assertStoppedWithLog("ACK", RAW);
		// Then — DB 반환 뒤 ACK 순서를 유지한다.
		var ordered = inOrder(queue, persistence);
		ordered.verify(queue).claimRaw(any());
		ordered.verify(persistence).persist(any(), any(), any());
		ordered.verify(queue).ackRaw(RAW);
		ordered.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("DB 처리 중 종료하면 commit과 ACK를 기다리고 새 claim을 하지 않는다")
	void shutdownWhileDbInFlight_waitsForCommitAndAckWithoutNewClaim() throws Exception {
		// Given — 실패 또는 종료 경계를 제어한다.
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(queue.claimRaw(any())).thenReturn(RAW);
		when(queue.ackRaw(RAW)).thenReturn(1L);
		when(persistence.persist(any(), any(), any())).thenAnswer(invocation -> {
			entered.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return 1;
		});
		CrewJoinPendingWorker worker = worker();
		var executor = Executors.newSingleThreadExecutor();
		try {
			// When — 처리 도중 종료를 요청한다.
			worker.start();
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			var stopped = stopAndAwaitBlocked(executor, worker);
			verify(queue, never()).ackRaw(any());
			release.countDown();
			// Then — 진행 중 호출 반환 뒤 종료하며 다음 claim은 없다.
			stopped.get(5, TimeUnit.SECONDS);
			assertThat(worker.isRunning()).isFalse();
			verify(queue, times(1)).claimRaw(any());
			verify(queue).ackRaw(RAW);
		} finally {
			release.countDown();
			worker.stop();
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("claim 중 종료하면 새 claim을 시작하지 않는다")
	void shutdownDuringClaim_doesNotStartAnotherClaim() throws Exception {
		// Given — 실패 또는 종료 경계를 제어한다.
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(queue.claimRaw(any())).thenAnswer(invocation -> {
			entered.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return null;
		});
		CrewJoinPendingWorker worker = worker();
		var executor = Executors.newSingleThreadExecutor();
		try {
			// When — 처리 도중 종료를 요청한다.
			worker.start();
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			var stopped = stopAndAwaitBlocked(executor, worker);
			release.countDown();
			// Then — 진행 중 호출 반환 뒤 종료하며 다음 claim은 없다.
			stopped.get(5, TimeUnit.SECONDS);
			assertThat(worker.isRunning()).isFalse();
			verify(queue, times(1)).claimRaw(any());
			verifyNoInteractions(persistence);
		} finally {
			release.countDown();
			worker.stop();
			executor.shutdownNow();
		}
	}

	private Future<?> stopAndAwaitBlocked(ExecutorService executor, CrewJoinPendingWorker worker) {
		var stopper = new AtomicReference<Thread>();
		Future<?> stopped = executor.submit(() -> {
			stopper.set(Thread.currentThread());
			worker.stop();
		});
		// stop()는 작업 완료 전에 반환하면 안 된다. 실행 중이 아니라 join·claim 대기에 들어간 것도 함께 확인한다.
		await().atMost(Duration.ofSeconds(2)).until(() -> !stopped.isDone() && stopper.get() != null
			&& stopper.get().getState() != Thread.State.RUNNABLE);
		return stopped;
	}

	private void assertStoppedWithLog(String stage, String raw) {
		Logger logger = (Logger)LoggerFactory.getLogger(CrewJoinPendingWorker.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		CrewJoinPendingWorker worker = worker();
		try {
			worker.start();
			await().atMost(Duration.ofSeconds(3)).until(() -> !worker.isRunning());
			worker.start();
			assertThat(worker.isRunning()).isFalse();
			assertThat(appender.list).hasSize(1);
			assertThat(appender.list.get(0).getFormattedMessage())
				.contains("CREW_JOIN_WORKER_STOPPED", "stage=" + stage, "namespace=unit", "runId=run-test", "cause=")
				.doesNotContain("payload=" + raw);
		} finally {
			worker.stop();
			logger.detachAppender(appender);
		}
	}

	private CrewJoinPendingWorker worker() {
		return new CrewJoinPendingWorker(queue, persistence, new ObjectMapper(), "unit", "run-test");
	}

	static Stream<String> malformedPayloads() {
		return Stream.of("bad-json", "[]", "null", "{}", RAW + " {}",
			RAW.replace("\"crewId\":\"crew\",", ""), RAW.replace("\"crewId\":\"crew\"", "\"crewId\":1"),
			RAW.replace("\"crewId\":\"crew\"", "\"crewId\":\" \""),
			RAW.replace("\"userId\":\"user\"", "\"userId\":null"),
			RAW.replace("\"userId\":\"user\"", "\"userId\":{},\"userId\":\"user\""),
			RAW.replace("{", "{\"extra\":\"x\","),
			RAW.replace("+09:00", "Z"), RAW.replace(".678", ""), RAW.replace(".678", ".6789"),
			RAW.replace("2026-01-02", "2026-02-30"));
	}
}
