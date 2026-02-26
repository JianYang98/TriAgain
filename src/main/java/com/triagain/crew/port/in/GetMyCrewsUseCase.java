package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.VerificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface GetMyCrewsUseCase {

    /** 내 크루 목록 조회 — 홈 화면에서 참여 중인 크루를 볼 때 사용 */
    List<CrewSummaryResult> getMyCrews(String userId);

    record CrewSummaryResult(
            String id,
            String name,
            String goal,
            VerificationType verificationType,
            int currentMembers,
            int maxMembers,
            CrewStatus status,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime createdAt
    ) {
    }
}
