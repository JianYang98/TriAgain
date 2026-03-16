package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivateRecruitingCrewsScheduler {

    private final CrewRepositoryPort crewRepositoryPort;

    /** 시작일 도래한 RECRUITING 크루 활성화 — 매일 00:00에 RECRUITING → ACTIVE 전환 */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void activateRecruitingCrews() {
        List<Crew> crews = crewRepositoryPort
                .findRecruitingCrewsStartedOnOrBefore(LocalDate.now());
        if (crews.isEmpty()) return;

        for (Crew crew : crews) {
            crew.activate();
            crewRepositoryPort.save(crew);
        }
        String crewIds = crews.stream()
                .map(Crew::getId)
                .collect(Collectors.joining(", "));
        log.info("크루 활성화 처리: {}건 | {}", crews.size(), crewIds);
    }
}
