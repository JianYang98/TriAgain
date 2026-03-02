package com.triagain.verification.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.out.SsePort;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompleteUploadSessionServiceTest {

    @Mock
    private UploadSessionRepositoryPort uploadSessionRepositoryPort;

    @Mock
    private SsePort ssePort;

    @InjectMocks
    private CompleteUploadSessionService completeUploadSessionService;

    private static UploadSession pendingSession(Long id) {
        return UploadSession.of(id, "user-1", "images/test.jpg", "image/jpeg",
                UploadSessionStatus.PENDING, LocalDateTime.now(), LocalDateTime.now());
    }

    private static UploadSession completedSession(Long id) {
        return UploadSession.of(id, "user-1", "images/test.jpg", "image/jpeg",
                UploadSessionStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now());
    }

    private static UploadSession expiredSession(Long id) {
        return UploadSession.of(id, "user-1", "images/test.jpg", "image/jpeg",
                UploadSessionStatus.EXPIRED, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("정상 완료 시 세션 상태 COMPLETED + afterCommit에서 SSE 전송")
    void complete_success() {
        // Given
        Long sessionId = 1L;
        UploadSession session = pendingSession(sessionId);
        given(uploadSessionRepositoryPort.findById(sessionId)).willReturn(Optional.of(session));

        // TransactionSynchronizationManager 초기화 (단위 테스트에서 afterCommit 콜백 등록 가능하도록)
        TransactionSynchronizationManager.initSynchronization();
        try {
            // When
            completeUploadSessionService.complete(sessionId);

            // Then
            assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
            verify(uploadSessionRepositoryPort).save(session);

            // afterCommit 콜백 수동 실행
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCommit());
            verify(ssePort).send(sessionId, "COMPLETED");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("존재하지 않는 세션 ID → UPLOAD_SESSION_NOT_FOUND 예외")
    void complete_sessionNotFound() {
        // Given
        Long sessionId = 999L;
        given(uploadSessionRepositoryPort.findById(sessionId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> completeUploadSessionService.complete(sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 COMPLETED인 세션 → 멱등 처리 (예외 없이 정상)")
    void complete_alreadyCompleted_idempotent() {
        // Given
        Long sessionId = 1L;
        UploadSession session = completedSession(sessionId);
        given(uploadSessionRepositoryPort.findById(sessionId)).willReturn(Optional.of(session));

        TransactionSynchronizationManager.initSynchronization();
        try {
            // When
            completeUploadSessionService.complete(sessionId);

            // Then — 상태 유지, save 호출, 예외 없음
            assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.COMPLETED);
            verify(uploadSessionRepositoryPort).save(session);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("EXPIRED 세션 → UPLOAD_SESSION_NOT_PENDING 예외")
    void complete_expiredSession() {
        // Given
        Long sessionId = 1L;
        UploadSession session = expiredSession(sessionId);
        given(uploadSessionRepositoryPort.findById(sessionId)).willReturn(Optional.of(session));

        // When & Then
        assertThatThrownBy(() -> completeUploadSessionService.complete(sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_PENDING);

        verify(uploadSessionRepositoryPort, never()).save(any());
    }
}
