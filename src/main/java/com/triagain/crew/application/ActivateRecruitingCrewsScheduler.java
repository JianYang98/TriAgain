package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
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
public class ActivateRecruitingCrewsScheduler {

    private final CrewRepositoryPort crewRepositoryPort;
    private final TransactionTemplate transactionTemplate;

    /** 시작일 도래한 RECRUITING 크루 활성화 — 매일 00:00에 RECRUITING → ACTIVE 전환 */
    @Scheduled(cron = "0 0 0 * * *")
    public void activateRecruitingCrews() {
        List<Crew> crews = crewRepositoryPort
                .findRecruitingCrewsStartedOnOrBefore(LocalDate.now());
        if (crews.isEmpty()) return;

        int successCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (Crew crew : crews) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    crew.activate();
                    crewRepositoryPort.save(crew);
                });
                successCount++;
            } catch (Exception e) {
                failedIds.add(crew.getId());
                log.error("크루 활성화 실패 [crewId={}]: {}", crew.getId(), e.getMessage(), e);
            }
        }

        log.info("크루 활성화 완료: 전체 {}건, 성공 {}건, 실패 {}건{}",
                crews.size(), successCount, failedIds.size(),
                failedIds.isEmpty() ? "" : " | 실패 ID: " + String.join(", ", failedIds));
    }
}
