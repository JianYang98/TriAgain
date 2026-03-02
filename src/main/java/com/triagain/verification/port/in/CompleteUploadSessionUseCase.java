package com.triagain.verification.port.in;

public interface CompleteUploadSessionUseCase {

    /** 업로드 세션 완료 처리 — Lambda 콜백 시 호출 */
    void complete(Long uploadSessionId);
}
