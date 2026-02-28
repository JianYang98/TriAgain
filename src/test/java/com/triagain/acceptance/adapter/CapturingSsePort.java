package com.triagain.acceptance.adapter;

import com.triagain.verification.port.out.SsePort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** SSE 이벤트 캡처용 테스트 빈 — SseEmitterAdapter를 대체하여 이벤트를 메모리에 저장 */
@Component
@Primary
public class CapturingSsePort implements SsePort {

    private final Map<Long, List<String>> capturedEvents = new ConcurrentHashMap<>();

    @Override
    public void subscribe(Long uploadSessionId, SseEmitter emitter) {
        // no-op — 테스트에서는 실제 SSE 스트림 불필요
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
