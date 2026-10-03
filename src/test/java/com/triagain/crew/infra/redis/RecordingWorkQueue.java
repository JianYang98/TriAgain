package com.triagain.crew.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.dao.QueryTimeoutException;

import com.triagain.crew.port.out.CrewJoinWorkQueuePort;

/**
 * 실제 Queue Port를 감싸 호출 시도를 세고, 지정한 경계에만 장애를 주입하는 테스트 협력자.
 * claim 횟수는 Redis 이동 결과가 아니라 Port 호출 시도다(P4-T8/T11).
 */
public final class RecordingWorkQueue implements CrewJoinWorkQueuePort, AutoCloseable {

	private final CrewJoinWorkQueuePort delegate;
	private final AtomicInteger recoverCalls = new AtomicInteger();
	private final AtomicInteger claimAttempts = new AtomicInteger();
	private final AtomicInteger ackCalls = new AtomicInteger();
	private final List<String> claimed = new CopyOnWriteArrayList<>();
	private final List<String> acked = new CopyOnWriteArrayList<>();
	private final List<Long> ackResults = new CopyOnWriteArrayList<>();
	private volatile int failRecoverAtCall;
	private volatile String ackFailure;
	private volatile Consumer<String> onRecoverReply = reply -> { };

	public RecordingWorkQueue(CrewJoinWorkQueuePort delegate) {
		this.delegate = delegate;
	}

	/** n번째 recovery 호출을 Redis 전송 전에 실패시킨다 — 확정 미이동 */
	public RecordingWorkQueue failRecoverAtCall(int call) {
		failRecoverAtCall = call;
		return this;
	}

	/** 첫 ACK 실패 모델 — before-send / zero / response-lost */
	public RecordingWorkQueue failFirstAck(String mode) {
		ackFailure = mode;
		return this;
	}

	/** 실제 LMOVE 응답을 받은 뒤 반환 직전에 실행 — 응답 경계 관측용 */
	public RecordingWorkQueue onRecoverReply(Consumer<String> hook) {
		onRecoverReply = hook;
		return this;
	}

	@Override
	public String recoverOneRaw() {
		if (recoverCalls.incrementAndGet() == failRecoverAtCall) {
			throw new QueryTimeoutException("injected recovery failure before send");
		}
		String reply = delegate.recoverOneRaw();
		onRecoverReply.accept(reply);
		return reply;
	}

	@Override
	public String claimRaw(Duration timeout) {
		claimAttempts.incrementAndGet();
		String raw = delegate.claimRaw(timeout);
		if (raw != null) {
			claimed.add(raw);
		}
		return raw;
	}

	@Override
	public long ackRaw(String raw) {
		ackCalls.incrementAndGet();
		acked.add(raw);
		String mode = ackFailure;
		ackFailure = null;
		if ("before-send".equals(mode)) {
			throw new IllegalStateException("injected ACK failure before send");
		}
		long removed = delegate.ackRaw(raw);
		if ("zero".equals(mode)) {
			removed = delegate.ackRaw(raw); // 서버가 이미 제거한 뒤의 실제 LREM 0행
		} else if ("response-lost".equals(mode)) {
			throw new IllegalStateException("injected ACK response lost");
		}
		ackResults.add(removed);
		return removed;
	}

	@Override
	public void close() throws Exception {
		if (delegate instanceof AutoCloseable closeable) {
			closeable.close();
		}
	}

	public int recoverCalls() {
		return recoverCalls.get();
	}

	public int claimAttempts() {
		return claimAttempts.get();
	}

	public int ackCalls() {
		return ackCalls.get();
	}

	public List<String> claimed() {
		return claimed;
	}

	public List<String> acked() {
		return acked;
	}

	public List<Long> ackResults() {
		return ackResults;
	}
}
