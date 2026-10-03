package com.triagain.crew.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;

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
		verify(queue, times(1)).recoverOneRaw();
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
		verify(queue, times(1)).recoverOneRaw();
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
		ordered.verify(queue).recoverOneRaw();
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

	@Test
	@DisplayName("P4-T6 일부 이동 뒤 recovery가 실패하면 worker를 시작하지 않고 claim·DB·ACK·추가 recovery가 없다")
	void partialRecoveryFailure_doesNotStartConsumeOrRetry() {
		// Given — C 한 건 이동 응답 뒤 다음 recovery 호출이 실패한다.
		when(queue.recoverOneRaw()).thenReturn("C").thenThrow(new QueryTimeoutException("injected"));
		ListAppender<ILoggingEvent> logs = capture();
		CrewJoinPendingWorker worker = worker();
		try {
			// When
			worker.start();
			// Then — 앱으로 예외를 던지지 않고, 소비 thread·claim·DB·ACK가 없다.
			assertThat(worker.isRunning()).isFalse();
			verify(queue, times(2)).recoverOneRaw();
			verify(queue, never()).claimRaw(any());
			verify(queue, never()).ackRaw(any());
			verifyNoInteractions(persistence);
			assertThat(messages(logs)).containsExactly(
				"CREW_JOIN_RECOVERY_STARTED namespace=unit runId=run-test",
				"CREW_JOIN_RECOVERY_FAILED stage=recover namespace=unit runId=run-test cause=QueryTimeoutException "
					+ "confirmedMoves=1 outcomeUnknown=true workerStarted=false retryInProcess=false "
					+ "admissionBlockedByRecovery=false");
		} finally {
			worker.stop();
			detach(logs);
		}
	}

	@Test
	@DisplayName("P4-T8 recovery가 실패한 인스턴스는 start를 다시 호출해도 recovery·소비를 다시 시도하지 않는다")
	void recoveryFailure_secondStartDoesNotRetry() {
		// Given — 연결 획득 실패(확정 미전송)
		when(queue.recoverOneRaw()).thenThrow(new DataAccessResourceFailureException("refused"));
		ListAppender<ILoggingEvent> logs = capture();
		CrewJoinPendingWorker worker = worker();
		try {
			// When — lifecycle이 다시 start를 호출하는 경우
			worker.start();
			worker.start();
			// Then — 시작 시도 latch가 thread 없이도 재진입을 막는다.
			assertThat(worker.isRunning()).isFalse();
			verify(queue, times(1)).recoverOneRaw();
			verifyNoMoreInteractions(queue);
			verifyNoInteractions(persistence);
			assertThat(messages(logs)).filteredOn(message -> message.startsWith("CREW_JOIN_RECOVERY_FAILED"))
				.singleElement().asString().contains("confirmedMoves=0", "outcomeUnknown=false");
		} finally {
			worker.stop();
			detach(logs);
		}
	}

	@Test
	@DisplayName("P4-T14 동시·중복 start에도 recovery 시퀀스와 소비 thread는 하나다")
	void concurrentStart_singleRecoverySequenceAndSingleThread() throws Exception {
		// Given — 첫 recovery 응답을 붙잡아 두 번째 start가 겹치게 한다.
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		holdFirstRecoveryReply(entered, release);
		Set<Thread> consumers = recordIdleClaimThreads();
		ListAppender<ILoggingEvent> logs = capture();
		CrewJoinPendingWorker worker = worker();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			// When — 첫 start가 recovery 중일 때 두 번째 start가 들어온다.
			Future<?> first = executor.submit(worker::start);
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> second = startAndAwaitBlocked(executor, worker);
			release.countDown();
			first.get(5, TimeUnit.SECONDS);
			second.get(5, TimeUnit.SECONDS);
			worker.start();
			// Then — LMOVE 세 번(x·y·nil)으로 된 시퀀스 1회, 소비 thread 1개
			await().atMost(Duration.ofSeconds(2)).until(() -> !consumers.isEmpty());
			verify(queue, times(3)).recoverOneRaw();
			assertThat(consumers).hasSize(1);
			assertThat(worker.isRunning()).isTrue();
			assertThat(messages(logs)).filteredOn(message -> message.startsWith("CREW_JOIN_RECOVERY_"))
				.containsExactly("CREW_JOIN_RECOVERY_STARTED namespace=unit runId=run-test",
					"CREW_JOIN_RECOVERY_SUCCEEDED namespace=unit runId=run-test confirmedMoves=2");
		} finally {
			release.countDown();
			worker.stop();
			executor.shutdownNow();
			detach(logs);
		}
	}

	@Test
	@DisplayName("P4-T14 WRONGTYPE 오류 응답은 nil(소진)로 숨기지 않고 recovery 실패로 끝난다")
	void wrongTypeReply_isRecoveryFailureNotDrained() {
		// Given — Lettuce 오류 응답이 번역되는 예외 형태
		when(queue.recoverOneRaw()).thenThrow(new RedisSystemException(
			"WRONGTYPE Operation against a key holding the wrong kind of value", null));
		ListAppender<ILoggingEvent> logs = capture();
		CrewJoinPendingWorker worker = worker();
		try {
			// When
			worker.start();
			// Then
			assertThat(worker.isRunning()).isFalse();
			verify(queue, never()).claimRaw(any());
			assertThat(messages(logs)).noneMatch(message -> message.contains("RECOVERY_SUCCEEDED"))
				.anyMatch(message -> message.contains("CREW_JOIN_RECOVERY_FAILED")
					&& message.contains("cause=RedisSystemException") && message.contains("workerStarted=false"));
		} finally {
			worker.stop();
			detach(logs);
		}
	}

	private Future<?> startAndAwaitBlocked(ExecutorService executor, CrewJoinPendingWorker worker) {
		var starter = new AtomicReference<Thread>();
		Future<?> started = executor.submit(() -> {
			starter.set(Thread.currentThread());
			worker.start();
		});
		// 첫 start가 recovery를 끝내기 전까지 두 번째 start는 monitor에서 기다려야 한다.
		await().atMost(Duration.ofSeconds(2)).until(() -> starter.get() != null
			&& starter.get().getState() == Thread.State.BLOCKED);
		return started;
	}

	private void holdFirstRecoveryReply(CountDownLatch entered, CountDownLatch release) {
		when(queue.recoverOneRaw()).thenAnswer(invocation -> {
			entered.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return "x";
		}).thenReturn("y", (String)null);
	}

	private Set<Thread> recordIdleClaimThreads() {
		Set<Thread> consumers = ConcurrentHashMap.newKeySet();
		when(queue.claimRaw(any())).thenAnswer(invocation -> {
			consumers.add(Thread.currentThread());
			Thread.sleep(5); // mock의 즉시 반환으로 인한 busy loop 완화 — 증명 수단 아님
			return null;
		});
		return consumers;
	}

	private ListAppender<ILoggingEvent> capture() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		((Logger)LoggerFactory.getLogger(CrewJoinPendingWorker.class)).addAppender(appender);
		return appender;
	}

	private void detach(ListAppender<ILoggingEvent> appender) {
		((Logger)LoggerFactory.getLogger(CrewJoinPendingWorker.class)).detachAppender(appender);
	}

	private List<String> messages(ListAppender<ILoggingEvent> appender) {
		return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
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
		ListAppender<ILoggingEvent> appender = capture();
		CrewJoinPendingWorker worker = worker();
		try {
			worker.start();
			await().atMost(Duration.ofSeconds(3)).until(() -> !worker.isRunning());
			worker.start();
			assertThat(worker.isRunning()).isFalse();
			var stopped = appender.list.stream()
				.filter(event -> event.getFormattedMessage().contains("CREW_JOIN_WORKER_STOPPED")).toList();
			assertThat(stopped).hasSize(1);
			assertThat(stopped.get(0).getFormattedMessage())
				.contains("CREW_JOIN_WORKER_STOPPED", "stage=" + stage, "namespace=unit", "runId=run-test", "cause=")
				.doesNotContain("payload=" + raw);
		} finally {
			worker.stop();
			detach(appender);
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
