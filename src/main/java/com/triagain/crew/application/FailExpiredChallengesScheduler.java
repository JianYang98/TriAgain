package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailExpiredChallengesScheduler {

    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final TransactionTemplate transactionTemplate;

    /** 마감 초과 챌린지 실패 처리 — 매 5분마다 크루별 deadlineTime 기준으로 판정 */
    @Scheduled(fixedRate = 300_000)
    public void failExpiredChallenges() {
        List<Challenge> expired = challengeRepositoryPort.findExpiredWithoutVerification();
        if (expired.isEmpty()) return;

        int successCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (Challenge challenge : expired) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    challenge.fail();
                    challengeRepositoryPort.save(challenge);
                });
                successCount++;
            } catch (Exception e) {
                failedIds.add(challenge.getId());
                log.error("챌린지 실패 처리 오류 [challengeId={}]: {}", challenge.getId(), e.getMessage(), e);
            }
        }

        log.info("챌린지 실패 처리 완료: 전체 {}건, 성공 {}건, 실패 {}건{}",
                expired.size(), successCount, failedIds.size(),
                failedIds.isEmpty() ? "" : " | 실패 ID: " + String.join(", ", failedIds));
    }
}
