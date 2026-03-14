package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface GetCrewByInviteCodeUseCase {

    /** 초대코드로 크루 미리보기 — 비멤버가 가입 전 크루 정보를 확인할 때 사용 */
    CrewInvitePreviewResult getByInviteCode(String inviteCode, String userId);

    record CrewInvitePreviewResult(
            String id,
            String creatorId,
            String name,
            String goal,
            String verificationContent,
            VerificationType verificationType,
            int maxMembers,
            int currentMembers,
            CrewStatus status,
            LocalDate startDate,
            LocalDate endDate,
            boolean allowLateJoin,
            LocalTime deadlineTime,
            LocalDateTime createdAt,
            List<MemberSummary> members,
            boolean joinable,
            String joinBlockedReason
    ) {}

    record MemberSummary(
            String userId,
            String nickname,
            String profileImageUrl,
            CrewRole role,
            LocalDateTime joinedAt
    ) {}
}
