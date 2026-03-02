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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteExpiredCrewsScheduler {

    private final CrewRepositoryPort crewRepositoryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;

    /** 기간 만료 크루 종료 처리 — 매일 03:00에 ACTIVE → COMPLETED 전환 + 남은 챌린지 ENDED */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void completeExpiredCrews() {
        List<Crew> expiredCrews = crewRepositoryPort
                .findActiveCrewsEndedBefore(LocalDate.now());
        if (expiredCrews.isEmpty()) return;

        for (Crew crew : expiredCrews) {
            List<Challenge> remaining = challengeRepositoryPort
                    .findAllByCrewIdAndStatus(crew.getId(), ChallengeStatus.IN_PROGRESS);
            for (Challenge challenge : remaining) {
                challenge.end();
                challengeRepositoryPort.save(challenge);
            }

            crew.complete();
            crewRepositoryPort.save(crew);
        }
        log.info("크루 종료 처리: {}건", expiredCrews.size());
    }
}