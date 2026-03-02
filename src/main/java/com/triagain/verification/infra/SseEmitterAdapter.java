package com.triagain.verification.infra;

import com.triagain.verification.port.in.SubscribeUploadSessionUseCase;
import com.triagain.verification.port.out.SsePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterAdapter implements SsePort, SubscribeUploadSessionUseCase {

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** SSE 구독 등록 — SseEmitter를 생성하고 관리 */
    @Override
    public Object subscribe(Long uploadSessionId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        emitters.put(uploadSessionId, emitter);
        emitter.onCompletion(() -> emitters.remove(uploadSessionId));
        emitter.onTimeout(() -> emitters.remove(uploadSessionId));
        emitter.onError(e -> emitters.remove(uploadSessionId));
        return emitter;
    }

    @Override
    public void send(Long uploadSessionId, String eventData) {
        SseEmitter emitter = emitters.remove(uploadSessionId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("upload-complete")
                    .data(eventData));
            emitter.complete();
        } catch (IOException e) {
            log.warn("SSE 전송 실패: uploadSessionId={}", uploadSessionId, e);
        }
    }
}
