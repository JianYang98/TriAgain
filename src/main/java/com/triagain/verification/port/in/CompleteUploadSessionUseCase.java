package com.triagain.verification.port.in;

public interface CompleteUploadSessionUseCase {

    /** 업로드 세션 완료 처리 — Lambda가 S3 key로 호출 */
    void complete(String imageKey);
}
