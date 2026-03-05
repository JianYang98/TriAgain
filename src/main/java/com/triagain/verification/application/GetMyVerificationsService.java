package com.triagain.verification.application;

import com.triagain.verification.port.in.GetMyVerificationsUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewPeriod;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMyVerificationsService implements GetMyVerificationsUseCase {

    private final CrewPort crewPort;
    private final ChallengePort challengePort;
    private final VerificationRepositoryPort verificationRepositoryPort;

    /** 내 인증 현황 조회 — 멤버십 검증 후 날짜·스트릭·달성 횟수 반환 */
    @Override
    @Transactional(readOnly = true)
    public MyVerificationsResult getMyVerifications(String crewId, String userId) {
        crewPort.validateMembership(crewId, userId);

        CrewPeriod period = crewPort.getCrewPeriod(crewId);
        List<LocalDate> verifiedDates = verificationRepositoryPort
                .findApprovedDatesByUserIdAndCrewId(userId, crewId, period.startDate(), period.endDate());

        int streakCount = calculateStreak(verifiedDates);
        int completedChallenges = challengePort.countCompletedChallenges(userId, crewId);

        MyProgress myProgress = challengePort.findActiveByUserIdAndCrewId(userId, crewId)
                .map(info -> new MyProgress(info.id(), info.status(), info.completedDays(), info.targetDays()))
                .orElse(null);

        return new MyVerificationsResult(verifiedDates, streakCount, completedChallenges, myProgress);
    }

    /** 최근 날짜부터 역방향 연속 인증 일수 계산 */
    private int calculateStreak(List<LocalDate> sortedDates) {
        if (sortedDates.isEmpty()) {
            return 0;
        }

        int streak = 1;
        for (int i = sortedDates.size() - 1; i > 0; i--) {
            LocalDate current = sortedDates.get(i);
            LocalDate previous = sortedDates.get(i - 1);
            if (current.minusDays(1).equals(previous)) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }
}
