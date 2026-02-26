package com.triagain.crew.application;

import com.triagain.crew.port.in.GetMyCrewsUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMyCrewsService implements GetMyCrewsUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    /** 내 크루 목록 조회 — 홈 화면에서 참여 중인 크루를 볼 때 사용 */
    @Override
    @Transactional(readOnly = true)
    public List<CrewSummaryResult> getMyCrews(String userId) {
        return crewRepositoryPort.findAllByUserId(userId).stream()
                .map(crew -> new CrewSummaryResult(
                        crew.getId(),
                        crew.getName(),
                        crew.getGoal(),
                        crew.getVerificationType(),
                        crew.getCurrentMembers(),
                        crew.getMaxMembers(),
                        crew.getStatus(),
                        crew.getStartDate(),
                        crew.getEndDate(),
                        crew.getCreatedAt()
                ))
                .toList();
    }
}
