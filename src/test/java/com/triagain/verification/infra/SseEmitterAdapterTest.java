package com.triagain.verification.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
}
