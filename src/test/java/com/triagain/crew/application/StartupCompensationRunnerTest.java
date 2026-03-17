package com.triagain.crew.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StartupCompensationRunnerTest {

    @Mock
    private ActivateRecruitingCrewsScheduler activateScheduler;

    @Mock
    private FailExpiredChallengesScheduler failScheduler;

    @Mock
    private CompleteExpiredCrewsScheduler completeScheduler;

    private StartupCompensationRunner runner;

    @BeforeEach
    void setUp() {
        runner = new StartupCompensationRunner(
                activateScheduler, failScheduler, completeScheduler);
    }

    @Test
    @DisplayName("3단계 모두 순서대로 실행됨 — 활성화 → 실패 → 종료")
    void allStepsExecutedInOrder() {
        // When
        runner.compensateMissedSchedulerJobs();

        // Then
        InOrder inOrder = inOrder(activateScheduler, failScheduler, completeScheduler);
        inOrder.verify(activateScheduler).activateRecruitingCrews();
        inOrder.verify(failScheduler).failExpiredChallenges();
        inOrder.verify(completeScheduler).completeExpiredCrews();
    }

    @Test
    @DisplayName("Step 1 실패해도 Step 2, 3 계속 진행")
    void step1Fails_step2And3StillRun() {
        // Given
        doThrow(new RuntimeException("DB error"))
                .when(activateScheduler).activateRecruitingCrews();

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
        doThrow(new RuntimeException("scheduler error"))
                .when(failScheduler).failExpiredChallenges();

        // When
        runner.compensateMissedSchedulerJobs();

        // Then
        verify(activateScheduler).activateRecruitingCrews();
        verify(completeScheduler).completeExpiredCrews();
    }
}
