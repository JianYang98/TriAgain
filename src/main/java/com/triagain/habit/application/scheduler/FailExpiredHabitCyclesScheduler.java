package com.triagain.habit.application.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessingResult;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.common.scheduler.FailedItem;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 마감+grace 초과 미인증 습관 사이클 실패 처리 — {@code FailExpiredChallengesScheduler} 구조 복제.
 * D13 락 미참여(07-10 결정) — fail()의 IN_PROGRESS 상태 가드 + stale 재조회로 셀프 경합 방지 충분(step1 §5 엣지 2).
 * 알림 발송 로직은 복제하지 않음(범위 외).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailExpiredHabitCyclesScheduler {

	private static final int CHUNK_SIZE = 50;

	private final HabitCycleRepositoryPort habitCycleRepositoryPort;
	private final ChunkProcessor chunkProcessor;
	private final DeadLetterRepositoryPort deadLetterRepositoryPort;
	private final Clock clock;

	/** 마감 초과 습관 사이클 실패 처리 — 5분마다 전량 스캔(Phase 1 규모 기준 안전, crew 패턴 대응) */
	@Scheduled(fixedDelay = 300_000)
	public void failExpiredHabitCycles() {
		List<HabitCycle> expired = habitCycleRepositoryPort.findExpiredWithoutVerification(LocalDateTime.now(clock));
		processExpired(expired);
	}

	private void processExpired(List<HabitCycle> expired) {
		if (expired.isEmpty()) {
			return;
		}

		ChunkProcessingResult<HabitCycle> result = chunkProcessor.execute(expired, CHUNK_SIZE, cycle -> {
			cycle.fail();
			habitCycleRepositoryPort.save(cycle);
		}, stale -> habitCycleRepositoryPort.findById(stale.getId()).orElseThrow());

		for (FailedItem<HabitCycle> failed : result.failedItems()) {
			deadLetterRepositoryPort.save(DeadLetter.of(
					DeadLetterTaskType.HABIT_CYCLE_FAIL,
					failed.item().getId(),
					failed.errorMessage()
			));
		}

		log.info("습관 사이클 실패 처리: 전체 {}건, 성공 {}건, 실패 {}건",
				expired.size(), result.successCount(), result.failedCount());
	}
}
