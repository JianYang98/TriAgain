package com.triagain.common.api;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.response.ApiResponse;
import com.triagain.crew.application.scheduler.ActivateRecruitingCrewsScheduler;
import com.triagain.crew.application.scheduler.CompleteExpiredCrewsScheduler;
import com.triagain.crew.application.scheduler.FailExpiredChallengesScheduler;
import com.triagain.support.application.scheduler.CrewStartNotificationScheduler;
import com.triagain.support.application.scheduler.ReminderScheduler;
import com.triagain.verification.application.scheduler.ExpireUploadSessionScheduler;

import lombok.extern.slf4j.Slf4j;

/**
 * 스케줄러 수동 트리거 — 현재 부하테스트용으로 유지한다.
 * prod 단독 프로필에는 등록되지 않으며, loadtest 프로필에서 활성화된다.
 * UseCase 경계·에러 응답·HTTP 테스트는 후속 PR에서 정리한다.
 */
@Slf4j
@RestController
@RequestMapping("/internal/scheduler")
@Profile("loadtest")
public class SchedulerTriggerController {

	private final Map<String, Runnable> schedulerMap;

	public SchedulerTriggerController(
			FailExpiredChallengesScheduler failExpiredScheduler,
			ActivateRecruitingCrewsScheduler activateScheduler,
			CompleteExpiredCrewsScheduler completeScheduler,
			ReminderScheduler reminderScheduler,
			CrewStartNotificationScheduler crewStartScheduler,
			ExpireUploadSessionScheduler expireSessionScheduler
	) {
		this.schedulerMap = Map.of(
			"fail-expired", failExpiredScheduler::failExpiredChallenges,
			"activate-crews", activateScheduler::activateRecruitingCrews,
			"complete-crews", completeScheduler::compensateAllExpiredCrews,
			"send-reminders", reminderScheduler::sendReminders,
			"crew-start-notifications", crewStartScheduler::sendCrewStartNotifications,
			"expire-upload-sessions", expireSessionScheduler::expirePendingSessions
		);
	}

	/** 지정 스케줄러를 동기 실행하고 소요 시간을 반환 — 부하테스트 측정용 */
	@PostMapping("/{name}")
	public ResponseEntity<ApiResponse<SchedulerTriggerResponse>> trigger(
			@PathVariable String name
	) {
		Runnable scheduler = schedulerMap.get(name);
		if (scheduler == null) {
			return ResponseEntity.badRequest().body(
				ApiResponse.ok(new SchedulerTriggerResponse(
					name, -1, LocalDateTime.now().toString(), LocalDateTime.now().toString()
				))
			);
		}

		log.info("[SchedulerTrigger] {} 시작", name);
		LocalDateTime startedAt = LocalDateTime.now();
		long startNanos = System.nanoTime();

		scheduler.run();

		long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
		LocalDateTime finishedAt = LocalDateTime.now();
		log.info("[SchedulerTrigger] {} 완료: {}ms", name, durationMs);

		return ResponseEntity.ok(ApiResponse.ok(
			new SchedulerTriggerResponse(name, durationMs,
				startedAt.toString(), finishedAt.toString())
		));
	}

	public record SchedulerTriggerResponse(
		String schedulerName,
		long durationMs,
		String startedAt,
		String finishedAt
	) {
	}
}
