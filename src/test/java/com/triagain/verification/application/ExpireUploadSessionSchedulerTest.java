package com.triagain.verification.application;

import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
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

    private ExpireUploadSessionScheduler scheduler;

    @BeforeEach
    void setUp() {
        // empty list early return 테스트에서 사용되지 않으므로 lenient
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        scheduler = new ExpireUploadSessionScheduler(uploadSessionRepositoryPort, transactionTemplate);
    }

    @Test
    @DisplayName("만료 대상 세션 없으면 save 호출 없음")
    void noPendingSessions_noSave() {
        // Given
        given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
                .willReturn(Collections.emptyList());

        // When
        scheduler.expirePendingSessions();

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
        scheduler.expirePendingSessions();

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
        scheduler.expirePendingSessions();

        // Then
        assertThat(session1.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        assertThat(session2.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepositoryPort, times(2)).save(any());
    }

    @Test
    @DisplayName("1건 실패해도 나머지는 정상 처리된다")
    void oneFailure_doesNotAffectOthers() {
        // Given
        UploadSession session1 = UploadSession.of(1L, "user-1", "crew-1", "key-1", "image/jpeg",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(20));
        UploadSession session2 = UploadSession.of(2L, "user-2", "crew-2", "key-2", "image/png",
                UploadSessionStatus.PENDING, LocalDateTime.now().minusMinutes(30), LocalDateTime.now().minusMinutes(30));

        given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
                .willReturn(List.of(session1, session2));
        given(uploadSessionRepositoryPort.save(any()))
                .willThrow(new RuntimeException("DB error"))    // 첫 번째 실패
                .willAnswer(inv -> inv.getArgument(0));         // 두 번째 성공

        // When & Then — 예외 전파 없음
        assertThatCode(() -> scheduler.expirePendingSessions())
                .doesNotThrowAnyException();

        // 두 번째 세션은 정상 처리
        assertThat(session2.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepositoryPort, times(2)).save(any());
    }
}
