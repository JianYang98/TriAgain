package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewRole;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface GetCrewUseCase {

    /** 크루 상세 조회 — 크루 멤버가 상세 화면을 볼 때 사용 */
    CrewDetailResult getCrew(String crewId, String userId);

    record CrewDetailResult(
            String id,
            String creatorId,
            String name,
            String goal,
            VerificationType verificationType,
            int maxMembers,
            int currentMembers,
            CrewStatus status,
            LocalDate startDate,
            LocalDate endDate,
            boolean allowLateJoin,
            String inviteCode,
            LocalDateTime createdAt,
            List<MemberResult> members
    ) {
    }

    record MemberResult(String userId, CrewRole role, LocalDateTime joinedAt) {
    }
}
