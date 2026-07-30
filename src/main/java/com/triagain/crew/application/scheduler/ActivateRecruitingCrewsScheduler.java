package com.triagain.crew.application.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessingResult;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.common.scheduler.FailedItem;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivateRecruitingCrewsScheduler {

	private static final int CHUNK_SIZE = 50;

	private final CrewRepositoryPort crewRepositoryPort;
	private final ChunkProcessor chunkProcessor;
	private final DeadLetterRepositoryPort deadLetterRepositoryPort;

	/** 시작일 도래한 RECRUITING 크루 활성화 — 매일 00:00에 RECRUITING → ACTIVE 전환 */
	@Scheduled(cron = "0 0 0 * * *")
	public void activateRecruitingCrews() {
		List<Crew> crews = crewRepositoryPort
				.findRecruitingCrewsStartedOnOrBefore(LocalDate.now());
		processCrews(crews);
	}

	/** 서버 시작 보정용 — 전체 미처리 건 조회 */
	public void compensateAllRecruitingCrews() {
		List<Crew> crews = crewRepositoryPort
				.findRecruitingCrewsStartedOnOrBefore(LocalDate.now());
		processCrews(crews);
	}

	private void processCrews(List<Crew> crews) {
		if (crews.isEmpty()) return;

		ChunkProcessingResult<Crew> result = chunkProcessor.execute(crews, CHUNK_SIZE, crew -> {
			crew.activate();
			crewRepositoryPort.save(crew);
		}, stale -> crewRepositoryPort.findById(stale.getId()).orElseThrow());

		for (FailedItem<Crew> failed : result.failedItems()) {
			deadLetterRepositoryPort.save(DeadLetter.of(
					DeadLetterTaskType.CREW_ACTIVATE,
					failed.item().getId(),
					failed.errorMessage()
			));
		}

		log.info("크루 활성화 처리: 전체 {}건, 성공 {}건, 실패 {}건",
				crews.size(), result.successCount(), result.failedCount());
	}
}
