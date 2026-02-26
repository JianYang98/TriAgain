package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.ActivateCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ActivateCrewService implements ActivateCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;

    /** 크루 활성화 — 시작일 도래 시 스케줄러가 호출 */
    @Override
    @Transactional
    public void activateCrew(String crewId, String requesterId) {
        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        if (!crew.getCreatorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.CREW_NOT_RECRUITING);
        }

        crew.activate();
        crewRepositoryPort.save(crew);

        LocalDate startDate = crew.getStartDate();
        LocalDateTime deadline = startDate.plusDays(3).atTime(23, 59, 59);

        crew.getMembers().forEach(member -> {
            Challenge challenge = Challenge.createFirst(
                    member.getUserId(),
                    crew.getId(),
                    startDate,
                    deadline
            );
            challengeRepositoryPort.save(challenge);
        });
    }
}
