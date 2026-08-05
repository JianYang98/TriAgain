package com.triagain.acceptance.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.triagain.verification.port.in.SubscribeUploadSessionUseCase;
import com.triagain.verification.port.out.SsePort;

/** SSE 이벤트 캡처용 테스트 빈 — SseEmitterAdapter를 대체하여 이벤트를 메모리에 저장 */
@Component
@Primary
public class CapturingSsePort implements SsePort, SubscribeUploadSessionUseCase {

	private final Map<Long, List<String>> capturedEvents = new ConcurrentHashMap<>();

	@Override
	public Object subscribe(Long uploadSessionId) {
		// no-op — 테스트에서는 실제 SSE 스트림 불필요
		return null;
	}

	@Override
	public void send(Long uploadSessionId, String eventData) {
		capturedEvents.computeIfAbsent(uploadSessionId, k -> new ArrayList<>()).add(eventData);
	}

	public List<String> getCapturedEvents(Long uploadSessionId) {
		return capturedEvents.getOrDefault(uploadSessionId, List.of());
	}

	public void clear() {
		capturedEvents.clear();
	}
}
