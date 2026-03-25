package com.triagain.crew.application.scheduler;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessingResult;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.common.scheduler.FailedItem;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailExpiredChallengesScheduler {

    private static final int CHUNK_SIZE = 50;
    private static final int WINDOW_MINUTES = 5;

    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final ChunkProcessor chunkProcessor;
    private final DeadLetterRepositoryPort deadLetterRepositoryPort;

    /** 마감 초과 챌린지 실패 처리 — 매 5분마다 윈도우 조회로 판정 */
    @Scheduled(fixedRate = 300_000)
    public void failExpiredChallenges() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusMinutes(WINDOW_MINUTES);
        List<Challenge> expired = challengeRepositoryPort.findExpiredInWindow(windowStart, now);
        processExpired(expired);
    }

    /** 서버 시작 보정용 — 전체 미처리 건 조회 */
    public void compensateAllExpired() {
        List<Challenge> expired = challengeRepositoryPort.findExpiredWithoutVerification();
        processExpired(expired);
    }

    private void processExpired(List<Challenge> expired) {
        if (expired.isEmpty()) return;

        ChunkProcessingResult<Challenge> result = chunkProcessor.execute(expired, CHUNK_SIZE, challenge -> {
            challenge.fail();
            challengeRepositoryPort.save(challenge);
        });

        for (FailedItem<Challenge> failed : result.failedItems()) {
            deadLetterRepositoryPort.save(DeadLetter.of(
                    DeadLetterTaskType.CHALLENGE_FAIL,
                    failed.item().getId(),
                    failed.errorMessage()
            ));
        }

        log.info("챌린지 실패 처리: 전체 {}건, 성공 {}건, 실패 {}건",
                expired.size(), result.successCount(), result.failedCount());
    }
}
