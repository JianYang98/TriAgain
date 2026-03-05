package com.triagain.crew.application;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.in.CreateCrewUseCase;
import com.triagain.crew.port.out.CrewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class CreateCrewService implements CreateCrewUseCase {

    private final CrewRepositoryPort crewRepositoryPort;

    @Value("${crew.max-duration-days:30}")
    private int maxDurationDays;

    /** 크루 생성 — 크루장을 리더 멤버로 자동 등록 */
    @Override
    @Transactional
    public CreateCrewResult createCrew(CreateCrewCommand command) {
        validateDuration(command);

        // 크루 생성 및 리더 지정
        Crew crew = Crew.create(
                command.creatorId(),
                command.name(),
                command.goal(),
                command.verificationType(),
                command.maxMembers(),
                command.startDate(),
                command.endDate(),
                command.allowLateJoin(),
                command.deadlineTime()
        );

        Crew saved = crewRepositoryPort.save(crew);
        crew.getMembers().forEach(crewRepositoryPort::saveMember); // 리더 멤버로 추가

        return new CreateCrewResult(
                saved.getId(),
                saved.getCreatorId(),
                saved.getName(),
                saved.getGoal(),
                saved.getVerificationType(),
                saved.getMaxMembers(),
                saved.getCurrentMembers(),
                saved.getStatus(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.isAllowLateJoin(),
                saved.getInviteCode(),
                saved.getCreatedAt(),
                saved.getDeadlineTime()
        );
    }

    /** 크루 기간 검증 — 최대 기간 초과 시 예외 */
    private void validateDuration(CreateCrewCommand command) {
        long days = ChronoUnit.DAYS.between(command.startDate(), command.endDate());
        if (days > maxDurationDays) {
            throw new BusinessException(ErrorCode.CREW_DURATION_TOO_LONG, maxDurationDays);
        }
    }
}
