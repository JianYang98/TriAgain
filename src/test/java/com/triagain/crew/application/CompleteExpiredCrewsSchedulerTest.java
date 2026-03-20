package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
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
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompleteExpiredCrewsSchedulerTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private ChallengeRepositoryPort challengeRepositoryPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    private CompleteExpiredCrewsScheduler scheduler;

    private static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59, 59);

    @BeforeEach
    void setUp() {
        // empty list early return 테스트에서 사용되지 않으므로 lenient
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        scheduler = new CompleteExpiredCrewsScheduler(crewRepositoryPort, challengeRepositoryPort, transactionTemplate);
    }

    @Test
    @DisplayName("만료 크루 없으면 save 호출 없음")
    void noExpiredCrews_noSave() {
        // Given
        given(crewRepositoryPort.findActiveCrewsEndedBefore(any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // When
        scheduler.completeExpiredCrews();

        // Then
        verify(crewRepositoryPort, never()).save(any());
        verify(challengeRepositoryPort, never()).findAllByCrewIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("만료 크루 → COMPLETED + IN_PROGRESS 챌린지 ENDED")
    void expiredCrew_completedAndChallengesEnded() {
        // Given
        Crew crew = activeCrew("crew-1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        Challenge remaining1 = Challenge.of("CHAL-1", "user-1", "crew-1", 1, 3, 1,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 2, 27),
                LocalDateTime.of(2026, 3, 2, 23, 59, 59), LocalDateTime.now());
        Challenge remaining2 = Challenge.of("CHAL-2", "user-2", "crew-1", 2, 3, 0,
                ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 2, 28),
                LocalDateTime.of(2026, 3, 3, 23, 59, 59), LocalDateTime.now());

        given(crewRepositoryPort.findActiveCrewsEndedBefore(any(LocalDate.class)))
                .willReturn(List.of(crew));
        given(challengeRepositoryPort.findAllByCrewIdAndStatus("crew-1", ChallengeStatus.IN_PROGRESS))
                .willReturn(List.of(remaining1, remaining2));
        given(challengeRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.completeExpiredCrews();

        // Then — 챌린지 2건 ENDED + 크루 COMPLETED
        assertThat(remaining1.getStatus()).isEqualTo(ChallengeStatus.ENDED);
        assertThat(remaining2.getStatus()).isEqualTo(ChallengeStatus.ENDED);
        assertThat(crew.getStatus()).isEqualTo(CrewStatus.COMPLETED);

        verify(challengeRepositoryPort, times(2)).save(any());
        verify(crewRepositoryPort).save(crew);
    }

    @Test
    @DisplayName("IN_PROGRESS 챌린지 없는 크루도 COMPLETED 전환 정상")
    void noChallengesRemaining_crewStillCompleted() {
        // Given
        Crew crew = activeCrew("crew-1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        given(crewRepositoryPort.findActiveCrewsEndedBefore(any(LocalDate.class)))
                .willReturn(List.of(crew));
        given(challengeRepositoryPort.findAllByCrewIdAndStatus("crew-1", ChallengeStatus.IN_PROGRESS))
                .willReturn(Collections.emptyList());
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        scheduler.completeExpiredCrews();

        // Then — 챌린지 save 없음, 크루만 COMPLETED
        assertThat(crew.getStatus()).isEqualTo(CrewStatus.COMPLETED);
        verify(challengeRepositoryPort, never()).save(any());
        verify(crewRepositoryPort).save(crew);
    }

    @Test
    @DisplayName("1건 실패해도 나머지 크루는 정상 처리된다")
    void oneFailure_doesNotAffectOthers() {
        // Given
        Crew crew1 = activeCrew("crew-1", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        Crew crew2 = activeCrew("crew-2", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        given(crewRepositoryPort.findActiveCrewsEndedBefore(any(LocalDate.class)))
                .willReturn(List.of(crew1, crew2));
        given(challengeRepositoryPort.findAllByCrewIdAndStatus("crew-1", ChallengeStatus.IN_PROGRESS))
                .willThrow(new RuntimeException("DB error"));
        given(challengeRepositoryPort.findAllByCrewIdAndStatus("crew-2", ChallengeStatus.IN_PROGRESS))
                .willReturn(Collections.emptyList());
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When & Then — 예외 전파 없음
        assertThatCode(() -> scheduler.completeExpiredCrews())
                .doesNotThrowAnyException();

        // crew-2는 정상 처리
        assertThat(crew2.getStatus()).isEqualTo(CrewStatus.COMPLETED);
        verify(crewRepositoryPort, times(1)).save(any());
    }

    // --- 헬퍼 메서드 ---

    private static Crew activeCrew(String id, LocalDate startDate, LocalDate endDate) {
        return Crew.of(id, "creator-1", "테스트 크루", "목표",
                "인증 내용", VerificationType.TEXT, 10, 1, CrewStatus.ACTIVE,
                startDate, endDate, false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, null, null, Collections.emptyList());
    }
}
