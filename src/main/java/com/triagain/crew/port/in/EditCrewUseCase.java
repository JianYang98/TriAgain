package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface EditCrewUseCase {

    /** 크루 정보 수정 — RECRUITING 상태에서 LEADER만 가능 */
    EditCrewResult editCrew(EditCrewCommand command);

    record EditCrewCommand(
            String userId,
            String crewId,
            String name,
            String goal,
            String verificationContent
    ) {}

    record EditCrewResult(
            String crewId,
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
            String inviteCode,
            LocalDateTime createdAt,
            LocalTime deadlineTime
    ) {}
}
