package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.ActivateCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivateCrewService implements ActivateCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

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
    }
}
