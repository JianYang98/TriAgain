package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface CreateCrewUseCase {

    /** 크루 생성 — 크루장을 리더 멤버로 자동 등록 */
    CreateCrewResult createCrew(CreateCrewCommand command);

    record CreateCrewCommand(
            String creatorId,
            String name,
            String goal,
            VerificationType verificationType,
            int maxMembers,
            LocalDate startDate,
            LocalDate endDate,
            boolean allowLateJoin
    ) {
    }

    record CreateCrewResult(
            String crewId,
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
            LocalDateTime createdAt
    ) {
    }
}
