package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
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
class FailExpiredChallengesSchedulerTest {

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    private FailExpiredChallengesScheduler scheduler;

    @BeforeEach
    void setUp() {
        // empty list early return 테스트에서 사용되지 않으므로 lenient
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        scheduler = new FailExpiredChallengesScheduler(challengeRepositoryPort, transactionTemplate);
    }

    @Test
    @DisplayName("만료 챌린지 없으면 save 호출 없음")
    void noExpiredChallenges_noSave() {
        // Given
        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(Collections.emptyList());

        // When
        scheduler.failExpiredChallenges();

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
        scheduler.failExpiredChallenges();

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
        scheduler.failExpiredChallenges();

        // Then — 2건 모두 FAILED, save 2회
        assertThat(expired1.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        assertThat(expired2.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        verify(challengeRepositoryPort, times(2)).save(any());
    }

    @Test
    @DisplayName("1건 실패해도 나머지는 정상 처리된다")
    void oneFailure_doesNotAffectOthers() {
        // Given
        Challenge expired1 = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());
        Challenge expired2 = Challenge.of("CHAL-2", "user-2", "crew-2", 2, 3, 1,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 3, 1),
                LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());

        given(challengeRepositoryPort.findExpiredWithoutVerification())
                .willReturn(List.of(expired1, expired2));
        given(challengeRepositoryPort.save(any()))
                .willThrow(new RuntimeException("DB error"))    // 첫 번째 실패
                .willAnswer(inv -> inv.getArgument(0));         // 두 번째 성공

        // When & Then — 예외 전파 없음
        assertThatCode(() -> scheduler.failExpiredChallenges())
                .doesNotThrowAnyException();

        // 두 번째 챌린지는 정상 처리
        assertThat(expired2.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        verify(challengeRepositoryPort, times(2)).save(any());
    }
}
