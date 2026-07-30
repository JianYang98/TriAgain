package com.triagain.crew.application;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.ChallengeStatus;
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
import java.util.function.Function;
import java.util.stream.Collectors;

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

		// ACTIVE 크루의 진행 중(IN_PROGRESS) 챌린지 진행도 배치 조회 (N+1 방지 — 크루당 최대 1개, crewId 키)
		Map<String, Challenge> activeChallenges = challengeRepositoryPort
				.findAllByUserIdAndCrewIdInAndStatus(userId, activeCrewIds, ChallengeStatus.IN_PROGRESS)
				.stream()
				.collect(Collectors.toMap(Challenge::getCrewId, Function.identity()));

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
				.map(crew -> toCrewSummaryResult(
						crew, verifiedCrewIds, successCounts, verifiedDayCounts, activeChallenges))
				.toList();
	}

	private CrewSummaryResult toCrewSummaryResult(
			Crew crew,
			Set<String> verifiedCrewIds,
			Map<String, Integer> successCounts,
			Map<String, Integer> verifiedDayCounts,
			Map<String, Challenge> activeChallenges) {
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
				crew.getInviteCode(),
				toChallengeProgress(activeChallenges.get(crew.getId()))
		);
	}

	/** 챌린지 → 진행도 변환 — 활성 챌린지 없으면 null (크루 상세 GetCrewService와 동일 매핑) */
	private CrewSummaryResult.ChallengeProgress toChallengeProgress(Challenge challenge) {
		if (challenge == null) {
			return null;
		}
		return new CrewSummaryResult.ChallengeProgress(
				challenge.getStatus().name(),
				challenge.getCompletedDays(),
				challenge.getTargetDays());
	}
}
