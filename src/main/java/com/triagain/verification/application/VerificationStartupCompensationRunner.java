package com.triagain.verification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 서버 시작 시 Verification 컨텍스트 밀린 스케줄러 작업 보정 — 업로드 세션 만료 처리 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationStartupCompensationRunner {

    private final ExpireUploadSessionScheduler expireSessionScheduler;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void compensateMissedSchedulerJobs() {
        log.info("[Verification Startup Compensation] 밀린 스케줄러 작업 보정 시작");

        try {
            expireSessionScheduler.compensateAllExpiredSessions();
        } catch (Exception e) {
            log.error("[Verification Startup Compensation] 업로드 세션 만료 보정 실패", e);
        }

        log.info("[Verification Startup Compensation] 보정 완료");
    }
}
