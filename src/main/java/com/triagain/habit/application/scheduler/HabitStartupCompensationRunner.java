package com.triagain.habit.application.scheduler;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 서버 시작 시 Habit 컨텍스트 밀린 스케줄러 작업 보정 — crew 보정(@Order(1)) 이후 실행되도록 별도 러너로 신설(step4 §6) */
@Slf4j
@Component
@RequiredArgsConstructor
public class HabitStartupCompensationRunner {

	private final FailExpiredHabitCyclesScheduler failExpiredHabitCyclesScheduler;

	@EventListener(ApplicationReadyEvent.class)
	@Order(2)
	public void compensateMissedHabitSchedulerJobs() {
		log.info("[Habit Startup Compensation] 밀린 스케줄러 작업 보정 시작");

		runStep("습관 사이클 실패 보정", failExpiredHabitCyclesScheduler::failExpiredHabitCycles);

		log.info("[Habit Startup Compensation] 보정 완료");
	}

	/** 개별 보정 단계 실행 — 한 단계 실패해도 다음 단계 계속 진행 */
	private void runStep(String stepName, Runnable step) {
		try {
			step.run();
		} catch (Exception e) {
			log.error("[Habit Startup Compensation] {} 실패", stepName, e);
		}
	}
}
