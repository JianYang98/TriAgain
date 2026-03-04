package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailExpiredChallengesScheduler {

    private final ChallengeRepositoryPort challengeRepositoryPort;

    /** 마감 초과 챌린지 실패 처리 — 매 5분마다 크루별 deadlineTime 기준으로 판정 */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void failExpiredChallenges() {
        List<Challenge> expired = challengeRepositoryPort.findExpiredWithoutVerification();
        if (expired.isEmpty()) return;

        for (Challenge challenge : expired) {
            challenge.fail();
            challengeRepositoryPort.save(challenge);
        }
        log.info("챌린지 실패 처리: {}건", expired.size());
    }
}
