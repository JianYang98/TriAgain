package com.triagain.verification.application.scheduler;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessingResult;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.common.scheduler.FailedItem;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireUploadSessionScheduler {

	private static final int EXPIRY_MINUTES = 15;
	private static final int CHUNK_SIZE = 50;

	private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
	private final ChunkProcessor chunkProcessor;
	private final DeadLetterRepositoryPort deadLetterRepositoryPort;

	/**
	 * PENDING 상태 세션 만료 처리 — 5분마다 전량 스캔 (Phase 1, 500명 규모 기준 안전).
	 * 15분 경과한 PENDING 세션을 EXPIRED로 전환.
	 * 윈도우+보정 이중 구조는 후속 과제 (future-considerations.md 2026-04-09 참조).
	 */
	@Scheduled(fixedDelay = 300_000)
	public void expirePendingSessions() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
		List<UploadSession> expiredSessions = uploadSessionRepositoryPort
				.findPendingSessionsBefore(threshold);
		processSessions(expiredSessions);
	}

	private void processSessions(List<UploadSession> sessions) {
		if (sessions.isEmpty()) return;

		ChunkProcessingResult<UploadSession> result = chunkProcessor.execute(sessions, CHUNK_SIZE, session -> {
			session.expire();
			uploadSessionRepositoryPort.save(session);
		}, stale -> uploadSessionRepositoryPort.findById(stale.getId()).orElseThrow());

		for (FailedItem<UploadSession> failed : result.failedItems()) {
			deadLetterRepositoryPort.save(DeadLetter.of(
					DeadLetterTaskType.SESSION_EXPIRE,
					String.valueOf(failed.item().getId()),
					failed.errorMessage()
			));
		}

		log.info("업로드 세션 만료 처리: 전체 {}건, 성공 {}건, 실패 {}건",
				sessions.size(), result.successCount(), result.failedCount());
	}
}
