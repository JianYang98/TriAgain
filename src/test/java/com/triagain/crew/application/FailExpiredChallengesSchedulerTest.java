package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FailExpiredChallengesSchedulerTest {

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @InjectMocks
    private FailExpiredChallengesScheduler scheduler;

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
}
