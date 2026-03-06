package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.UserPort;
import com.triagain.crew.port.out.UserPort.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetCrewByInviteCodeService implements GetCrewByInviteCodeUseCase {

    private final CrewRepositoryPort crewRepositoryPort;
    private final UserPort userPort;

    /** 초대코드로 크루 미리보기 — 비멤버가 가입 전 크루 정보를 확인할 때 사용 */
    @Override
    @Transactional(readOnly = true)
    public CrewInvitePreviewResult getByInviteCode(String inviteCode, String userId) {
        Crew crew = crewRepositoryPort.findByInviteCode(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

        List<String> memberUserIds = crew.getMembers().stream()
                .map(m -> m.getUserId())
                .toList();
        Map<String, UserProfile> profiles = userPort.findProfilesByIds(memberUserIds);

        List<MemberSummary> members = crew.getMembers().stream()
                .map(m -> {
                    UserProfile profile = profiles.get(m.getUserId());
                    return new MemberSummary(
                            m.getUserId(),
                            profile != null ? profile.nickname() : null,
                            profile != null ? profile.profileImageUrl() : null,
                            m.getRole(),
                            m.getJoinedAt()
                    );
                })
                .toList();

        String joinBlockedReason = calculateJoinBlockedReason(crew, userId);
        return new CrewInvitePreviewResult(
                crew.getId(),
                crew.getCreatorId(),
                crew.getName(),
                crew.getGoal(),
                crew.getVerificationType(),
                crew.getMaxMembers(),
                crew.getCurrentMembers(),
                crew.getStatus(),
                crew.getStartDate(),
                crew.getEndDate(),
                crew.isAllowLateJoin(),
                crew.getDeadlineTime(),
                crew.getCreatedAt(),
                members,
                joinBlockedReason == null,
                joinBlockedReason
        );
    }

    /** 가입 차단 사유 계산 — null이면 가입 가능 */
    private String calculateJoinBlockedReason(Crew crew, String userId) {
        if (crew.getMembers().stream().anyMatch(m -> m.getUserId().equals(userId))) {
            return "ALREADY_MEMBER";
        }
        if (crew.getStatus() == CrewStatus.COMPLETED) {
            return "CREW_ENDED";
        }
        if (crew.isFull()) {
            return "CREW_FULL";
        }
        if (crew.getStatus() == CrewStatus.ACTIVE && !crew.isAllowLateJoin()) {
            return "LATE_JOIN_NOT_ALLOWED";
        }
        if (crew.isJoinDeadlinePassed()) {
            return "CREW_JOIN_DEADLINE_PASSED";
        }
        return null;
    }
}
