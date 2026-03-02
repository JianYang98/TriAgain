package com.triagain.verification.port.in;

public interface SubscribeUploadSessionUseCase {

    /** SSE 구독 등록 — 클라이언트가 업로드 완료 이벤트를 수신할 때 사용 */
    Object subscribe(Long uploadSessionId);
}
