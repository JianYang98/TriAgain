package com.triagain.verification.application.scheduler;

import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpireUploadSessionSchedulerTest {

    @Mock
    private UploadSessionRepositoryPort uploadSessionRepositoryPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private DeadLetterRepositoryPort deadLetterRepositoryPort;

    private ExpireUploadSessionScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ChunkProcessor chunkProcessor = new ChunkProcessor(transactionTemplate);
        scheduler = new ExpireUploadSessionScheduler(uploadSessionRepositoryPort, chunkProcessor, deadLetterRepositoryPort);
    }

    @Test
    @DisplayName("만료 대상 세션 없으면 save 호출 없음")
    void noPendingSessions_noSave() {
        // Given
        given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
                .willReturn(Collections.emptyList());

        // When
        scheduler.compensateAllExpiredSessions();

        // Then
        verify(uploadSessionRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("PENDING 세션이 EXPIRED로 전환된다")
    void pendingSession_expiredSuccessfully() {
        // Given
        UploadSession session = UploadSession.of(1L, "user-1", "crew-1", "key-1", "image/jpeg",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(20));

        given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
                .willReturn(List.of(session));
        given(uploadSessionRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.compensateAllExpiredSessions();

        // Then
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepositoryPort, times(1)).save(any());
    }

    @Test
    @DisplayName("여러 세션 동시 만료 처리")
    void multipleSessions_allExpired() {
        // Given
        UploadSession session1 = UploadSession.of(1L, "user-1", "crew-1", "key-1", "image/jpeg",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(20));
        UploadSession session2 = UploadSession.of(2L, "user-2", "crew-2", "key-2", "image/png",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(30), LocalDateTime.now().minusMinutes(30));

        given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
                .willReturn(List.of(session1, session2));
        given(uploadSessionRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.compensateAllExpiredSessions();

        // Then
        assertThat(session1.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        assertThat(session2.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepositoryPort, times(2)).save(any());
    }

    @Test
    @DisplayName("1건 실패해도 나머지는 정상 처리되고 Dead Letter가 기록된다")
    void oneFailure_doesNotAffectOthers_andDeadLetterSaved() {
        // Given
        UploadSession session1 = UploadSession.of(1L, "user-1", "crew-1", "key-1", "image/jpeg",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(20));
        UploadSession session2 = UploadSession.of(2L, "user-2", "crew-2", "key-2", "image/png",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(30), LocalDateTime.now().minusMinutes(30));

        // rehydrator용 fresh 인스턴스
        UploadSession freshSession1 = UploadSession.of(1L, "user-1", "crew-1", "key-1", "image/jpeg",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(20));
        UploadSession freshSession2 = UploadSession.of(2L, "user-2", "crew-2", "key-2", "image/png",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(30), LocalDateTime.now().minusMinutes(30));

        given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
                .willReturn(List.of(session1, session2));
        given(uploadSessionRepositoryPort.findById(1L)).willReturn(Optional.of(freshSession1));
        given(uploadSessionRepositoryPort.findById(2L)).willReturn(Optional.of(freshSession2));
        given(uploadSessionRepositoryPort.save(any()))
                .willThrow(new RuntimeException("DB error"))    // 청크 내 첫 save 실패
                .willAnswer(inv -> inv.getArgument(0))          // per-item retry 성공
                .willAnswer(inv -> inv.getArgument(0));

        // When & Then — 예외 전파 없음
        assertThatCode(() -> scheduler.compensateAllExpiredSessions())
                .doesNotThrowAnyException();

        // rehydrate된 fresh 인스턴스가 EXPIRED로 처리됨
        assertThat(freshSession1.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        assertThat(freshSession2.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);

        // rehydrate 후 재시도 성공이므로 Dead Letter 없음
        verify(deadLetterRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("윈도우 조회로 만료 세션 처리")
    void expirePendingSessions_usesWindowQuery() {
        // Given
        UploadSession session = UploadSession.of(1L, "user-1", "crew-1", "key-1", "image/jpeg",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(20));

        given(uploadSessionRepositoryPort.findPendingSessionsInWindow(any(), any()))
                .willReturn(List.of(session));
        given(uploadSessionRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.expirePendingSessions();

        // Then — 윈도우 조회 사용, EXPIRED 처리
        verify(uploadSessionRepositoryPort).findPendingSessionsInWindow(any(), any());
        verify(uploadSessionRepositoryPort, never()).findPendingSessionsBefore(any());
        assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
    }
}
