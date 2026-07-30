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
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteExpiredCrewsScheduler {

	private static final int CHUNK_SIZE = 50;

	private final CrewRepositoryPort crewRepositoryPort;
	private final ChallengeRepositoryPort challengeRepositoryPort;
	private final ChunkProcessor chunkProcessor;
	private final DeadLetterRepositoryPort deadLetterRepositoryPort;

	/** 기간 만료 크루 종료 처리 — 매일 00:05에 ACTIVE → COMPLETED 전환 + 남은 챌린지 ENDED */
	@Scheduled(cron = "0 5 0 * * *")
	public void completeExpiredCrews() {
		List<Crew> expiredCrews = crewRepositoryPort
				.findActiveCrewsEndedBefore(LocalDate.now());
		processCrews(expiredCrews);
	}

	/** 서버 시작 보정용 — 전체 미처리 건 조회 */
	public void compensateAllExpiredCrews() {
		List<Crew> expiredCrews = crewRepositoryPort
				.findActiveCrewsEndedBefore(LocalDate.now());
		processCrews(expiredCrews);
	}

	private void processCrews(List<Crew> expiredCrews) {
		if (expiredCrews.isEmpty()) {
			return;
		}

		ChunkProcessingResult<Crew> result = chunkProcessor.execute(expiredCrews, CHUNK_SIZE, crew -> {
			List<Challenge> remaining = challengeRepositoryPort
					.findAllByCrewIdAndStatus(crew.getId(), ChallengeStatus.IN_PROGRESS);
			for (Challenge challenge : remaining) {
				challenge.end();
				challengeRepositoryPort.save(challenge);
			}
			crew.complete();
			crewRepositoryPort.save(crew);
		}, stale -> crewRepositoryPort.findById(stale.getId()).orElseThrow());

		for (FailedItem<Crew> failed : result.failedItems()) {
			deadLetterRepositoryPort.save(DeadLetter.of(
					DeadLetterTaskType.CREW_COMPLETE,
					failed.item().getId(),
					failed.errorMessage()
			));
		}

		log.info("크루 종료 처리: 전체 {}건, 성공 {}건, 실패 {}건",
				expiredCrews.size(), result.successCount(), result.failedCount());
	}
}
