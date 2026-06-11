package com.triagain.crew.infra;

import com.triagain.crew.port.out.VerificationQueryPort;
import com.triagain.verification.port.in.CheckTodayVerificationUseCase;
import com.triagain.verification.port.in.CountApprovedDaysUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Crew → Verification 컨텍스트 간 어댑터 — CheckTodayVerificationUseCase/CountApprovedDaysUseCase에 위임 */
@Component
@RequiredArgsConstructor
public class VerificationQueryAdapter implements VerificationQueryPort {

    private final CheckTodayVerificationUseCase checkTodayVerificationUseCase;
    private final CountApprovedDaysUseCase countApprovedDaysUseCase;

    @Override
    public Set<String> findVerifiedCrewIds(String userId, List<String> crewIds, LocalDate targetDate) {
        return checkTodayVerificationUseCase.findVerifiedCrewIds(userId, crewIds, targetDate);
    }

    @Override
    public Map<String, Integer> findApprovedDayCountsByCrewIds(String userId, List<String> crewIds) {
        return countApprovedDaysUseCase.countApprovedDaysByCrewIds(userId, crewIds);
    }
}
