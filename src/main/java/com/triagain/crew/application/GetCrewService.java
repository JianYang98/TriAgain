package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.in.GetCrewUseCase;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.UserPort;
import com.triagain.crew.port.out.UserPort.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetCrewService implements GetCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final UserPort userPort;

    /** 크루 상세 조회 — 크루 멤버가 상세 화면을 볼 때 사용 */
    @Override
    @Transactional(readOnly = true)
    public CrewDetailResult getCrew(String crewId, String userId) {
        Crew crew = crewRepositoryPort.findById(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));

        boolean isMember = crew.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            throw new BusinessException(ErrorCode.CREW_ACCESS_DENIED);
        }

        // 크루 상세에서 멤버별 현황 표시에 사용
        Map<String, Challenge> activeChallenges = challengeRepositoryPort
                .findAllByCrewIdAndStatus(crewId, ChallengeStatus.IN_PROGRESS).stream()
                .collect(Collectors.toMap(Challenge::getUserId, Function.identity()));

        // 크루 멤버별 성공 횟수 조회 — 작심삼일 성공 카운트에 사용
        Map<String, Integer> successCounts = challengeRepositoryPort.countSuccessByCrewId(crewId);

        List<String> memberUserIds = crew.getMembers().stream()
                .map(m -> m.getUserId())
                .toList();
        Map<String, UserProfile> profiles = userPort.findProfilesByIds(memberUserIds);

        // 멤버 + 프로필 + 상세 조합
        List<MemberResult> members = crew.getMembers().stream()
                .map(m -> toMemberResult(m, activeChallenges, successCounts, profiles))
                .toList();

        return new CrewDetailResult(
                crew.getId(),
                crew.getCreatorId(),
                crew.getName(),
                crew.getGoal(),
                crew.getVerificationContent(),
                crew.getVerificationType(),
                crew.getMaxMembers(),
                crew.getCurrentMembers(),
                crew.getStatus(),
                crew.getStartDate(),
                crew.getEndDate(),
                crew.isAllowLateJoin(),
                crew.getInviteCode(),
                crew.getCreatedAt(),
                crew.getDeadlineTime(),
                members
        );
    }

    /** 멤버 도메인 → MemberResult 변환 — 프로필/챌린지/성공횟수 조합 */
    private MemberResult toMemberResult(CrewMember member,
                                        Map<String, Challenge> activeChallenges,
                                        Map<String, Integer> successCounts,
                                        Map<String, UserProfile> profiles) {
        Challenge challenge = activeChallenges.get(member.getUserId());
        ChallengeProgress progress = challenge != null
                ? new ChallengeProgress(
                        challenge.getStatus().name(),
                        challenge.getCompletedDays(),
                        challenge.getTargetDays())
                : null;
        int successCount = successCounts.getOrDefault(member.getUserId(), 0);
        UserProfile profile = profiles.get(member.getUserId());
        String nickname = profile != null ? profile.nickname() : null;
        String profileImageUrl = profile != null ? profile.profileImageUrl() : null;
        return new MemberResult(member.getUserId(), nickname, profileImageUrl,
                member.getRole(), member.getJoinedAt(), successCount, progress);
    }
}
