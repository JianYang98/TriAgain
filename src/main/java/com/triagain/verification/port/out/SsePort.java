package com.triagain.verification.port.out;

public interface SsePort {

	/** SSE 구독 등록 — emitter 를 생성해 세션 id 로 보관한다 */
	Object subscribe(Long uploadSessionId);

	/** SSE 이벤트 전송 — Lambda 콜백 시 구독 중인 클라이언트에 알림 */
	void send(Long uploadSessionId, String eventData);
}
