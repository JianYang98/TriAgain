package com.triagain.crew.application.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 서버 시작 시 Crew 컨텍스트 밀린 스케줄러 작업 보정 — 활성화 → 실패 → 종료 순서 보장 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupCompensationRunner {

    private final ActivateRecruitingCrewsScheduler activateScheduler;
    private final FailExpiredChallengesScheduler failScheduler;
    private final CompleteExpiredCrewsScheduler completeScheduler;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void compensateMissedSchedulerJobs() {
        log.info("[Crew Startup Compensation] 밀린 스케줄러 작업 보정 시작");

        runStep("크루 활성화 보정", activateScheduler::compensateAllRecruitingCrews);
        runStep("챌린지 실패 보정", failScheduler::failExpiredChallenges);
        runStep("크루 종료 보정", completeScheduler::compensateAllExpiredCrews);

        log.info("[Crew Startup Compensation] 보정 완료");
    }

    /** 개별 보정 단계 실행 — 한 단계 실패해도 다음 단계 계속 진행 */
    private void runStep(String stepName, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            log.error("[Crew Startup Compensation] {} 실패", stepName, e);
        }
    }
}
