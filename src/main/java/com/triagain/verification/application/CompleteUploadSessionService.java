package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.in.CompleteUploadSessionUseCase;
import com.triagain.verification.port.out.SsePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CompleteUploadSessionService implements CompleteUploadSessionUseCase {

    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final SsePort ssePort;

    /** 업로드 세션 완료 + SSE 알림 — Lambda가 S3 업로드 성공 감지 시 호출 */
    @Override
    @Transactional
    public void complete(Long uploadSessionId) {
        UploadSession session = uploadSessionRepositoryPort.findById(uploadSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

        session.complete();
        uploadSessionRepositoryPort.save(session);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        ssePort.send(uploadSessionId, "COMPLETED");
                    }
                }
        );
    }
}
