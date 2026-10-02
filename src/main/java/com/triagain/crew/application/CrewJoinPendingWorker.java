package com.triagain.crew.application;

import java.time.Duration;

import org.springframework.context.SmartLifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.triagain.crew.port.out.CrewJoinWorkQueuePort;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrewJoinPendingWorker implements SmartLifecycle {

	public static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(1);
	private static final long SHUTDOWN_WAIT_MILLIS = 10_000;

	private final CrewJoinWorkQueuePort queue;
	private final CrewJoinPersistenceService persistence;
	private final ObjectReader reader;
	private final String namespace;
	private final String runId;
	private final Object claimMonitor = new Object();
	private volatile boolean stopRequested;
	private Thread thread;

	/** 단일 소비 루프 구성 — 별도 reader로 기존 API의 Jackson 설정 보존 */
	public CrewJoinPendingWorker(CrewJoinWorkQueuePort queue, CrewJoinPersistenceService persistence,
		ObjectMapper mapper, String namespace, String runId) {
		this.queue = queue;
		this.persistence = persistence;
		this.reader = mapper.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
			DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		this.namespace = namespace;
		this.runId = runId;
	}

	/** 한 인스턴스에서 한 번만 시작 — 오류 중단 뒤 자동·수동 재시작 없음 */
	@Override
	public synchronized void start() {
		if (thread != null) {
			return;
		}
		thread = new Thread(this::consume, "crew-join-worker");
		thread.setDaemon(true);
		thread.start();
	}

	private void consume() {
		while (!stopRequested) {
			String raw;
			try {
				raw = claimUnlessStopped();
			} catch (RuntimeException exception) {
				logStopped("claim", null, exception);
				return;
			}
			if (raw != null && !process(raw)) {
				return;
			}
		}
	}

	private String claimUnlessStopped() {
		synchronized (claimMonitor) {
			return stopRequested ? null : queue.claimRaw(BLOCK_TIMEOUT);
		}
	}

	private boolean process(String raw) {
		String stage = "parse";
		String crewId = null;
		try {
			CrewJoinPendingPayload payload = CrewJoinPendingPayload.parse(raw, reader);
			crewId = payload.crewId();
			stage = "DB";
			persistence.persist(crewId, payload.userId(), payload.joinedAt());
			stage = "ACK";
			if (queue.ackRaw(raw) != 1) {
				throw new IllegalStateException("ACK did not remove exactly one raw payload");
			}
			return true;
		} catch (RuntimeException exception) {
			logStopped(stage, crewId, exception);
			return false;
		}
	}

	private void logStopped(String stage, String crewId, RuntimeException exception) {
		stopRequested = true;
		log.error("CREW_JOIN_WORKER_STOPPED stage={} namespace={} runId={} crewId={} cause={} reason={}",
			stage, namespace, runId, crewId, exception.getClass().getSimpleName(), safeReason(stage), exception);
	}

	private String safeReason(String stage) {
		return switch (stage) {
			case "claim" -> "claim outcome unknown; preserve queue state";
			case "parse" -> "invalid payload; no ACK";
			case "DB" -> "persistence failed; no ACK";
			default -> "ACK failed or outcome unknown; DB already committed";
		};
	}

	/** 신규 claim 중단 후 진행 중 작업의 commit·ACK를 유한 시간 기다림 — interrupt하지 않음 */
	@Override
	public void stop() {
		stopRequested = true;
		synchronized (claimMonitor) {
			// 이미 gate를 통과한 claim의 반환을 기다린다. 이후 새 claim은 금지한다.
		}
		Thread current;
		synchronized (this) {
			current = thread;
		}
		if (current == null || current == Thread.currentThread()) {
			return;
		}
		try {
			current.join(SHUTDOWN_WAIT_MILLIS);
			if (current.isAlive()) {
				log.warn("CREW_JOIN_WORKER_SHUTDOWN_INCOMPLETE namespace={} runId={}", namespace, runId);
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	/** 소비 스레드 생존 여부 — HTTP health나 admission 차단과 연결하지 않음 */
	@Override
	public synchronized boolean isRunning() {
		return thread != null && thread.isAlive();
	}
}
