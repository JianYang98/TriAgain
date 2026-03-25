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
    private static final int WINDOW_MINUTES = 5;

    private final UploadSessionRepositoryPort uploadSessionRepositoryPort;
    private final ChunkProcessor chunkProcessor;
    private final DeadLetterRepositoryPort deadLetterRepositoryPort;

    /** PENDING 상태 세션 만료 처리 — 5분 윈도우 내 15분 경과 세션을 EXPIRED로 전환 */
    @Scheduled(fixedRate = 300_000)
    public void expirePendingSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(EXPIRY_MINUTES);
        LocalDateTime windowFrom = now.minusMinutes(EXPIRY_MINUTES + WINDOW_MINUTES);
        List<UploadSession> expiredSessions = uploadSessionRepositoryPort
                .findPendingSessionsInWindow(windowFrom, threshold);
        processSessions(expiredSessions);
    }

    /** 서버 시작 보정용 — 전체 미처리 건 조회 */
    public void compensateAllExpiredSessions() {
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
