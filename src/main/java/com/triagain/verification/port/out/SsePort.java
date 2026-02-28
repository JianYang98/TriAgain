package com.triagain.verification.port.out;

public interface SsePort {

    /** SSE 이벤트 전송 — Lambda 콜백 시 구독 중인 클라이언트에 알림 */
    void send(Long uploadSessionId, String eventData);
}
