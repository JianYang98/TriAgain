package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteExpiredCrewsScheduler {

    private final CrewRepositoryPort crewRepositoryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final TransactionTemplate transactionTemplate;

    /** 기간 만료 크루 종료 처리 — 매일 00:05에 ACTIVE → COMPLETED 전환 + 남은 챌린지 ENDED */
    @Scheduled(cron = "0 5 0 * * *")
    public void completeExpiredCrews() {
        List<Crew> expiredCrews = crewRepositoryPort
                .findActiveCrewsEndedBefore(LocalDate.now());
        if (expiredCrews.isEmpty()) return;

        int successCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (Crew crew : expiredCrews) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    List<Challenge> remaining = challengeRepositoryPort
                            .findAllByCrewIdAndStatus(crew.getId(), ChallengeStatus.IN_PROGRESS);
                    for (Challenge challenge : remaining) {
                        challenge.end();
                        challengeRepositoryPort.save(challenge);
                    }
                    crew.complete();
                    crewRepositoryPort.save(crew);
                });
                successCount++;
            } catch (Exception e) {
                failedIds.add(crew.getId());
                log.error("크루 종료 처리 실패 [crewId={}]: {}", crew.getId(), e.getMessage(), e);
            }
        }

        log.info("크루 종료 처리 완료: 전체 {}건, 성공 {}건, 실패 {}건{}",
                expiredCrews.size(), successCount, failedIds.size(),
                failedIds.isEmpty() ? "" : " | 실패 ID: " + String.join(", ", failedIds));
    }
}
