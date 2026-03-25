package com.triagain.crew.application;

import com.triagain.verification.application.ExpireUploadSessionScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 서버 시작 시 밀린 스케줄러 작업 보정 — 활성화 → 실패 → 종료 → 세션 만료 순서 보장 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupCompensationRunner {

    private final ActivateRecruitingCrewsScheduler activateScheduler;
    private final FailExpiredChallengesScheduler failScheduler;
    private final CompleteExpiredCrewsScheduler completeScheduler;
    private final ExpireUploadSessionScheduler expireSessionScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void compensateMissedSchedulerJobs() {
        log.info("[Startup Compensation] 밀린 스케줄러 작업 보정 시작");

        // 순서 중요: 활성화 → 실패 → 종료 → 세션 만료
        runStep("크루 활성화 보정", activateScheduler::compensateAllRecruitingCrews);
        runStep("챌린지 실패 보정", failScheduler::compensateAllExpired);
        runStep("크루 종료 보정", completeScheduler::compensateAllExpiredCrews);
        runStep("업로드 세션 만료 보정", expireSessionScheduler::compensateAllExpiredSessions);

        log.info("[Startup Compensation] 보정 완료");
    }

    /** 개별 보정 단계 실행 — 한 단계 실패해도 다음 단계 계속 진행 */
    private void runStep(String stepName, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            log.error("[Startup Compensation] {} 실패", stepName, e);
        }
    }
}
