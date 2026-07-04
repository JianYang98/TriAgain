package com.triagain.crew.application;

import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.port.in.GetMyCrewsUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.VerificationQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetMyCrewsService implements GetMyCrewsUseCase {

    private final CrewRepositoryPort crewRepositoryPort;
    private final VerificationQueryPort verificationQueryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;

    /** 내 크루 목록 조회 — 홈 화면에서 참여 중인 크루를 볼 때 사용 */
    @Override
    @Transactional(readOnly = true)
    public List<CrewSummaryResult> getMyCrews(String userId) {
        List<Crew> crews = crewRepositoryPort.findAllByUserId(userId);

        // ACTIVE 크루만 대상으로 오늘 인증 여부 배치 조회 (N+1 방지)
        List<String> activeCrewIds = crews.stream()
                .filter(crew -> crew.getStatus() == CrewStatus.ACTIVE)
                .map(Crew::getId)
                .toList();

        Set<String> verifiedCrewIds = verificationQueryPort.findVerifiedCrewIds(
                userId, activeCrewIds, LocalDate.now());

        // COMPLETED 크루만 대상으로 성취 지표 배치 조회 (N+1 방지 — todayVerified 패턴 복제)
        List<String> completedCrewIds = crews.stream()
                .filter(crew -> crew.getStatus() == CrewStatus.COMPLETED)
                .map(Crew::getId)
                .toList();

        Map<String, Integer> successCounts =
                challengeRepositoryPort.findSuccessCountsByUserIdAndCrewIds(userId, completedCrewIds);
        Map<String, Integer> verifiedDayCounts =
                verificationQueryPort.findApprovedDayCountsByCrewIds(userId, completedCrewIds);

        return crews.stream()
                .map(crew -> toCrewSummaryResult(crew, verifiedCrewIds, successCounts, verifiedDayCounts))
                .toList();
    }

    private CrewSummaryResult toCrewSummaryResult(
            Crew crew,
            Set<String> verifiedCrewIds,
            Map<String, Integer> successCounts,
            Map<String, Integer> verifiedDayCounts) {
        return new CrewSummaryResult(
                crew.getId(),
                crew.getName(),
                crew.getGoal(),
                crew.getVerificationContent(),
                crew.getVerificationType(),
                crew.getCurrentMembers(),
                crew.getMaxMembers(),
                crew.getStatus(),
                crew.getStartDate(),
                crew.getEndDate(),
                crew.getCreatedAt(),
                crew.getCategory(),
                crew.getVisibility(),
                verifiedCrewIds.contains(crew.getId()),
                successCounts.getOrDefault(crew.getId(), 0),
                verifiedDayCounts.getOrDefault(crew.getId(), 0),
                crew.getInviteCode()
        );
    }
}
