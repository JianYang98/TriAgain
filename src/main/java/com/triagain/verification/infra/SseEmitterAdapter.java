package com.triagain.verification.infra;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.triagain.verification.port.in.SubscribeUploadSessionUseCase;
import com.triagain.verification.port.out.SsePort;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SseEmitterAdapter implements SsePort, SubscribeUploadSessionUseCase {

	private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

	/** SSE 구독 등록 — SseEmitter를 생성하고 관리 */
	@Override
	public Object subscribe(Long uploadSessionId) {
		SseEmitter emitter = new SseEmitter(60_000L);
		emitters.put(uploadSessionId, emitter);
		// 키만으로 지우면 안 된다 — 재구독으로 교체된 뒤 버려진 emitter 의 콜백이 뒤늦게 발화하면
		// 살아있는 새 emitter 를 지워버리고, 이후 send() 가 조용히 return 해 업로드 완료가 배달되지 않는다.
		emitter.onCompletion(() -> emitters.remove(uploadSessionId, emitter));
		emitter.onTimeout(() -> emitters.remove(uploadSessionId, emitter));
		emitter.onError(e -> emitters.remove(uploadSessionId, emitter));
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
