package com.triagain.verification.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterAdapterTest {

	private static final long DEFAULT_TIMEOUT_MS = 60_000L;

	private SseEmitterAdapter sseEmitterAdapter;

	@BeforeEach
	void setUp() {
		sseEmitterAdapter = new SseEmitterAdapter(DEFAULT_TIMEOUT_MS);
	}

	@Test
	@DisplayName("subscribe → send → SseEmitter 반환 + 이벤트 전송 완료")
	void subscribe_then_send_success() {
		// Given
		Long sessionId = 1L;
		Object result = sseEmitterAdapter.subscribe(sessionId);

		// Then — subscribe 결과는 SseEmitter
		assertThat(result).isInstanceOf(SseEmitter.class);

		// When — send 호출 시 예외 없이 정상 처리
		assertThatCode(() -> sseEmitterAdapter.send(sessionId, "COMPLETED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("구독 없이 send → 예외 없이 무시 (null-safe)")
	void send_withoutSubscription_ignored() {
		// When & Then
		assertThatCode(() -> sseEmitterAdapter.send(999L, "COMPLETED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("같은 ID로 subscribe 두 번 → 기존 emitter 덮어쓰기")
	void subscribe_twice_overwritesPrevious() {
		// Given
		Long sessionId = 1L;
		Object first = sseEmitterAdapter.subscribe(sessionId);
		Object second = sseEmitterAdapter.subscribe(sessionId);

		// Then — 서로 다른 emitter 인스턴스
		assertThat(first).isNotSameAs(second);

		// send는 두 번째 emitter로 전송 — 예외 없이 정상
		assertThatCode(() -> sseEmitterAdapter.send(sessionId, "COMPLETED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("send 후 동일 ID로 재전송 → emitter 이미 제거됨, 무시")
	void send_twice_secondIgnored() {
		// Given
		Long sessionId = 1L;
		sseEmitterAdapter.subscribe(sessionId);
		sseEmitterAdapter.send(sessionId, "COMPLETED");

		// When — 두 번째 send는 emitter가 이미 제거됨
		assertThatCode(() -> sseEmitterAdapter.send(sessionId, "COMPLETED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("재구독 후 버려진 emitter의 콜백(완료·타임아웃·에러)이 뒤늦게 발화해도 새 emitter는 살아남는다")
	void staleCallbacks_doNotEvictCurrentEmitter() throws Exception {
		for (String callback : List.of("completionCallback", "timeoutCallback", "errorCallback")) {
			// Given — 네트워크 끊김 등으로 같은 세션이 재구독한 상황
			SseEmitterAdapter adapter = new SseEmitterAdapter(DEFAULT_TIMEOUT_MS);
			Long sessionId = 1L;
			SseEmitter stale = (SseEmitter) adapter.subscribe(sessionId);
			SseEmitter current = (SseEmitter) adapter.subscribe(sessionId);

			// When — 버려진 emitter의 콜백이 뒤늦게 발화 (SseEmitter 타임아웃은 60초)
			fireCallback(stale, callback);
			adapter.send(sessionId, "COMPLETED");

			// Then — 배달됐다면 send()가 current.complete()를 불렀으므로 재전송이 거부된다.
			// 예외 검사로는 이 버그를 못 잡는다 — send()는 배달에 실패해도 조용히 return 하기 때문이다.
			assertThatThrownBy(() -> current.send("again"))
					.as("%s 발화 후 새 emitter로 배달", callback)
					.isInstanceOf(IllegalStateException.class);
		}
	}

	/**
	 * 타임아웃 시 emitter.complete()가 실제로 AsyncRequestTimeoutException·500을 막는지는 순수 단위
	 * 테스트로 관측 불가능하다(Handler는 package-private, ResponseBodyEmitter 바이트코드 확인 완료 —
	 * 2026-08-23). 그 증명은 실제 서블릿 비동기 계층을 태우는
	 * {@link com.triagain.verification.api.UploadSessionSseTimeoutApiTest}가 맡는다
	 * (emitter.complete() 삭제 시 500으로 레드 확인됨 — M6).
	 *
	 * <p>아래 {@code fireCallback}은 SseEmitter의 lifecycle 콜백이 서블릿 비동기 계층에서만 자연
	 * 발화하는 것을 등록된 실제 프로덕션 람다를 직접 실행시켜 우회한다. Spring 내부 필드명에
	 * 의존하므로, 업그레이드로 이름이 바뀌면 이 테스트가 시끄럽게 깨진다(의도된 것).
	 */
	@SuppressWarnings("unchecked")
	private void fireCallback(SseEmitter emitter, String fieldName) throws Exception {
		Field field = ResponseBodyEmitter.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		Object holder = field.get(emitter);
		if (holder instanceof Runnable runnable) {
			runnable.run();
		} else {
			((Consumer<Throwable>) holder).accept(new IllegalStateException("테스트용 강제 에러"));
		}
	}
}
