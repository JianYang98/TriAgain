package com.triagain.crew.port.in;

import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
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
            String verificationContent,
            VerificationType verificationType,
            int currentMembers,
            int maxMembers,
            CrewStatus status,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime createdAt,
            CrewCategory category,
            CrewVisibility visibility,
            boolean todayVerified,
            int successCount,      // 요청자의 작심삼일(SUCCESS 챌린지) 횟수 — COMPLETED만 실집계, 그 외 0
            int verifiedDayCount   // 요청자의 APPROVED 인증 일수 — COMPLETED만 실집계, 그 외 0
    ) {
    }
}
