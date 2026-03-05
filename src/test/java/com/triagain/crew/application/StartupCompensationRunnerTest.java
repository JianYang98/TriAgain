package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;
import com.triagain.crew.port.out.CrewRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StartupCompensationRunnerTest {

    @Mock
    private CrewRepositoryPort crewRepositoryPort;

    @Mock
    private FailExpiredChallengesScheduler failScheduler;

    @Mock
    private CompleteExpiredCrewsScheduler completeScheduler;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private TransactionStatus transactionStatus;

    private StartupCompensationRunner runner;

    private static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59, 59);

    @BeforeEach
    void setUp() {
        given(txManager.getTransaction(any())).willReturn(transactionStatus);
        runner = new StartupCompensationRunner(
                crewRepositoryPort, failScheduler, completeScheduler, txManager);
    }

    @Test
    @DisplayName("밀린 RECRUITING 크루 → ACTIVE 전환")
    void recruitingCrews_activated() {
        // Given
        Crew crew1 = recruitingCrew("crew-1", LocalDate.of(2026, 3, 1));
        Crew crew2 = recruitingCrew("crew-2", LocalDate.of(2026, 3, 2));
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(List.of(crew1, crew2));
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        runner.compensateMissedSchedulerJobs();

        // Then
        assertThat(crew1.getStatus()).isEqualTo(CrewStatus.ACTIVE);
        assertThat(crew2.getStatus()).isEqualTo(CrewStatus.ACTIVE);
        verify(crewRepositoryPort, times(2)).save(any());
        verify(failScheduler).failExpiredChallenges();
        verify(completeScheduler).completeExpiredCrews();
    }

    @Test
    @DisplayName("밀린 크루 없으면 save 호출 없음")
    void noRecruitingCrews_noSave() {
        // Given
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // When
        runner.compensateMissedSchedulerJobs();

        // Then
        verify(crewRepositoryPort, never()).save(any());
        verify(failScheduler).failExpiredChallenges();
        verify(completeScheduler).completeExpiredCrews();
    }

    @Test
    @DisplayName("3단계 모두 순서대로 실행됨")
    void allStepsExecutedInOrder() {
        // Given
        Crew crew = recruitingCrew("crew-1", LocalDate.of(2026, 3, 1));
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(List.of(crew));
        given(crewRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        runner.compensateMissedSchedulerJobs();

        // Then — 순서: 크루 활성화 → 챌린지 실패 → 크루 종료
        InOrder inOrder = inOrder(crewRepositoryPort, failScheduler, completeScheduler);
        inOrder.verify(crewRepositoryPort).save(any());
        inOrder.verify(failScheduler).failExpiredChallenges();
        inOrder.verify(completeScheduler).completeExpiredCrews();
    }

    @Test
    @DisplayName("Step 1 실패해도 Step 2, 3 계속 진행")
    void step1Fails_step2And3StillRun() {
        // Given
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willThrow(new RuntimeException("DB error"));

        // When
        runner.compensateMissedSchedulerJobs();

        // Then
        verify(failScheduler).failExpiredChallenges();
        verify(completeScheduler).completeExpiredCrews();
    }

    @Test
    @DisplayName("Step 2 실패해도 Step 3 계속 진행")
    void step2Fails_step3StillRuns() {
        // Given
        given(crewRepositoryPort.findRecruitingCrewsStartedOnOrBefore(any(LocalDate.class)))
                .willReturn(Collections.emptyList());
        doThrow(new RuntimeException("scheduler error"))
                .when(failScheduler).failExpiredChallenges();

        // When
        runner.compensateMissedSchedulerJobs();

        // Then
        verify(completeScheduler).completeExpiredCrews();
    }

    // --- 헬퍼 메서드 ---

    private static Crew recruitingCrew(String id, LocalDate startDate) {
        return Crew.of(id, "creator-1", "테스트 크루", "목표",
                VerificationType.TEXT, 10, 1, CrewStatus.RECRUITING,
                startDate, startDate.plusDays(30), false, "ABC123",
                LocalDateTime.now(), DEADLINE_TIME, Collections.emptyList());
    }
}
