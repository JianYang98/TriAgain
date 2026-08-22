package com.triagain.verification.infra;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.triagain.verification.port.out.SsePort;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SseEmitterAdapter implements SsePort {

	private final long sseTimeoutMs;

	private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitterAdapter(@Value("${triagain.verification.sse-timeout-ms:60000}") long sseTimeoutMs) {
		this.sseTimeoutMs = sseTimeoutMs;
	}

	/** SSE 구독 등록 — SseEmitter를 생성하고 관리 */
	@Override
	public Object subscribe(Long uploadSessionId) {
		SseEmitter emitter = new SseEmitter(sseTimeoutMs);
		emitters.put(uploadSessionId, emitter);
		// 키만으로 지우면 안 된다 — 재구독으로 교체된 뒤 버려진 emitter 의 콜백이 뒤늦게 발화하면
		// 살아있는 새 emitter 를 지워버리고, 이후 send() 가 조용히 return 해 업로드 완료가 배달되지 않는다.
		emitter.onCompletion(() -> emitters.remove(uploadSessionId, emitter));
		// complete() 를 먼저 불러야 정상 종료로 처리된다 — 안 부르면 60초 타임아웃이
		// AsyncRequestTimeoutException 으로 전파돼 GlobalExceptionHandler 의 catch-all 에서
		// "처리되지 않은 예외: null" 500 로그로 찍힌다(revisions/03 수정 A).
		emitter.onTimeout(() -> {
			emitter.complete();
			emitters.remove(uploadSessionId, emitter);
		});
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
