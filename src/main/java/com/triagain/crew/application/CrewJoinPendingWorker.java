package com.triagain.crew.application;

import java.time.Duration;

import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessResourceFailureException;

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
	private boolean startAttempted;
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

	/** 한 인스턴스에서 한 번만 시작 — recovery 성공 뒤에만 소비, 실패·오류 중단 뒤 자동·수동 재시작 없음 */
	@Override
	public synchronized void start() {
		// thread 생성 전에 기록한다 — recovery 실패로 thread가 없어도 같은 실행의 재진입을 막는다.
		if (startAttempted) {
			return;
		}
		startAttempted = true;
		if (!recover()) {
			return;
		}
		thread = new Thread(this::consume, "crew-join-worker");
		thread.setDaemon(true);
		thread.start();
	}

	/** 이전 실행의 processing을 정상 nil까지 되돌림 — 실패는 refresh로 전파하지 않고 worker를 시작하지 않음 */
	private boolean recover() {
		log.info("CREW_JOIN_RECOVERY_STARTED namespace={} runId={}", namespace, runId);
		int confirmedMoves = 0;
		try {
			while (queue.recoverOneRaw() != null) {
				confirmedMoves++;
			}
		} catch (RuntimeException exception) {
			// 연결 획득 실패만 확정 미전송이다. 그 외(timeout·단절·오류 응답)는 실행 여부를 추정하지 않는다.
			boolean outcomeUnknown = !(exception instanceof DataAccessResourceFailureException);
			log.error("CREW_JOIN_RECOVERY_FAILED stage=recover namespace={} runId={} cause={} confirmedMoves={} "
					+ "outcomeUnknown={} workerStarted=false retryInProcess=false admissionBlockedByRecovery=false",
				namespace, runId, exception.getClass().getSimpleName(), confirmedMoves, outcomeUnknown, exception);
			return false;
		}
		log.info("CREW_JOIN_RECOVERY_SUCCEEDED namespace={} runId={} confirmedMoves={}",
			namespace, runId, confirmedMoves);
		return true;
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
