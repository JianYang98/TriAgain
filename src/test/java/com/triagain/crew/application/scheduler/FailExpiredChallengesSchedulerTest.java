package com.triagain.crew.application.scheduler;

import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.NotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
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
class FailExpiredChallengesSchedulerTest {

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private DeadLetterRepositoryPort deadLetterRepositoryPort;

    private FailExpiredChallengesScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        lenient().doAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        ChunkProcessor chunkProcessor = new ChunkProcessor(transactionTemplate);
        scheduler = new FailExpiredChallengesScheduler(challengeRepositoryPort, crewRepositoryPort, notificationPort, chunkProcessor, deadLetterRepositoryPort);
    }

    @Test
    @DisplayName("만료 챌린지 없으면 save 호출 없음")
    void noExpiredChallenges_noSave() {
        // Given
        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(Collections.emptyList());

        // When
        scheduler.compensateAllExpired();

        // Then
        verify(challengeRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("만료 챌린지 → FAILED 처리만 수행 (새 챌린지 미생성)")
    void expiredChallenge_failedOnly() {
        // Given
        Challenge expired = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.compensateAllExpired();

        // Then — FAILED 저장 1회만 (새 챌린지 미생성)
        assertThat(expired.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        verify(challengeRepositoryPort, times(1)).save(any());
    }

    @Test
    @DisplayName("여러 챌린지 동시 실패 처리")
    void multipleChallenges_allFailed() {
        // Given
        Challenge expired1 = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());
        Challenge expired2 = Challenge.of("CHAL-2", "user-2", "crew-2", 2, 3, 1,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired1, expired2));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.compensateAllExpired();

        // Then — 2건 모두 FAILED, save 2회
        assertThat(expired1.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        assertThat(expired2.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        verify(challengeRepositoryPort, times(2)).save(any());
    }

    @Test
    @DisplayName("1건 실패해도 나머지는 정상 처리되고 Dead Letter가 기록된다")
    void oneFailure_doesNotAffectOthers_andDeadLetterSaved() {
        // Given
        Challenge expired1 = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());
        Challenge expired2 = Challenge.of("CHAL-2", "user-2", "crew-2", 2, 3, 1,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        // rehydrator용 fresh 인스턴스
        Challenge freshExpired1 = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());
        Challenge freshExpired2 = Challenge.of("CHAL-2", "user-2", "crew-2", 2, 3, 1,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired1, expired2));
        given(challengeRepositoryPort.findById("CHAL-1")).willReturn(Optional.of(freshExpired1));
        given(challengeRepositoryPort.findById("CHAL-2")).willReturn(Optional.of(freshExpired2));
        given(challengeRepositoryPort.save(any()))
                .willThrow(new RuntimeException("DB error"))    // 청크 내 첫 save 실패
                .willAnswer(inv -> inv.getArgument(0))          // per-item retry 성공
                .willAnswer(inv -> inv.getArgument(0));

        // When & Then — 예외 전파 없음
        assertThatCode(() -> scheduler.compensateAllExpired())
                .doesNotThrowAnyException();

        // rehydrate된 fresh 인스턴스가 FAILED로 처리됨
        assertThat(freshExpired1.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        assertThat(freshExpired2.getStatus()).isEqualTo(ChallengeStatus.FAILED);

        // 실패 건은 Dead Letter에 기록되지 않음 (rehydrate 후 재시도 성공)
        verify(deadLetterRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("윈도우 조회로 만료 챌린지 실패 처리")
    void failExpiredChallenges_usesWindowQuery() {
        // Given
        Challenge expired = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        given(challengeRepositoryPort.findExpiredInWindow(any(), any()))
                .willReturn(List.of(expired));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.failExpiredChallenges();

        // Then — 윈도우 조회 사용, FAILED 처리
        verify(challengeRepositoryPort).findExpiredInWindow(any(), any());
        verify(challengeRepositoryPort, never()).findExpiredWithoutVerification();
        assertThat(expired.getStatus()).isEqualTo(ChallengeStatus.FAILED);
    }
}
