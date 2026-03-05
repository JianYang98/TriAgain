package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

/** 서버 시작 시 밀린 스케줄러 작업 보정 — 활성화 → 실패 → 종료 순서 보장 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupCompensationRunner {

    private final CrewRepositoryPort crewRepositoryPort;
    private final FailExpiredChallengesScheduler failScheduler;
    private final CompleteExpiredCrewsScheduler completeScheduler;
    private final PlatformTransactionManager txManager;

    @EventListener(ApplicationReadyEvent.class)
    public void compensateMissedSchedulerJobs() {
        log.info("[Startup Compensation] 밀린 스케줄러 작업 보정 시작");

        // 순서 중요: 활성화 → 실패 → 종료
        runStep("크루 활성화 보정", this::compensateCrewActivation);
        runStep("챌린지 실패 보정", failScheduler::failExpiredChallenges);
        runStep("크루 종료 보정", completeScheduler::completeExpiredCrews);

        log.info("[Startup Compensation] 보정 완료");
    }

    private void compensateCrewActivation() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            List<Crew> crews = crewRepositoryPort
                    .findRecruitingCrewsStartedOnOrBefore(LocalDate.now());
            if (crews.isEmpty()) return;

            for (Crew crew : crews) {
                crew.activate();
                crewRepositoryPort.save(crew);
            }
            log.info("[Startup Compensation] 크루 활성화 보정: {}건", crews.size());
        });
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
