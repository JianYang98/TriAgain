package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailExpiredChallengesScheduler {

    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final CrewRepositoryPort crewRepositoryPort;

    /** 마감 초과 챌린지 실패 처리 — 매 1분마다 크루별 deadlineTime 기준으로 판정 */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void failExpiredChallenges() {
        List<Challenge> expired = challengeRepositoryPort.findExpiredWithoutVerification();
        if (expired.isEmpty()) return;

        Map<String, Crew> crewMap = loadCrewMap(expired);
        int failedCount = 0;
        int newCount = 0;

        for (Challenge challenge : expired) {
            challenge.fail();
            challengeRepositoryPort.save(challenge);
            failedCount++;

            Crew crew = crewMap.get(challenge.getCrewId());
            if (crew == null) continue;

            LocalDate missedDate = challenge.getStartDate()
                    .plusDays(challenge.getCompletedDays());
            LocalDate newStartDate = missedDate.plusDays(1);

            if (crew.getEndDate().isBefore(newStartDate)) continue;

            LocalDateTime newDeadline = newStartDate.plusDays(3)
                    .atTime(crew.getDeadlineTime());
            Challenge next = Challenge.createNext(
                    challenge.getUserId(), challenge.getCrewId(),
                    challenge.getCycleNumber(), newStartDate, newDeadline);
            challengeRepositoryPort.save(next);
            newCount++;
        }
        log.info("챌린지 실패 처리: {}건, 새 챌린지 생성: {}건", failedCount, newCount);
    }

    private Map<String, Crew> loadCrewMap(List<Challenge> challenges) {
        List<String> crewIds = challenges.stream()
                .map(Challenge::getCrewId)
                .distinct()
                .toList();
        return crewRepositoryPort.findAllByIds(crewIds).stream()
                .collect(Collectors.toMap(Crew::getId, Function.identity()));
    }
}
