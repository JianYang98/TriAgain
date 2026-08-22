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

	private SseEmitterAdapter sseEmitterAdapter;

	@BeforeEach
	void setUp() {
		sseEmitterAdapter = new SseEmitterAdapter();
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
			SseEmitterAdapter adapter = new SseEmitterAdapter();
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

	@Test
	@DisplayName("타임아웃 콜백이 발화하면 해당 emitter가 정상 종료된다 — AsyncRequestTimeoutException 방지")
	void onTimeout_completesEmitter() throws Exception {
		// Given
		SseEmitterAdapter adapter = new SseEmitterAdapter();
		SseEmitter emitter = (SseEmitter) adapter.subscribe(1L);

		// When — 타임아웃 콜백 발화
		fireCallback(emitter, "timeoutCallback");

		// Then — complete() 가 불려 emitter 가 이미 종료된 상태다
		assertThatThrownBy(() -> emitter.send("x")).isInstanceOf(IllegalStateException.class);
	}

	/**
	 * SseEmitter의 lifecycle 콜백은 서블릿 비동기 계층이 호출하므로 단위 테스트에서 자연 발화하지 않는다.
	 * 등록된 실제 프로덕션 람다를 그대로 실행시키기 위해 콜백 홀더를 꺼내 돌린다.
	 * Spring 내부 필드명에 의존하므로, 업그레이드로 이름이 바뀌면 이 테스트가 시끄럽게 깨진다(의도된 것).
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
