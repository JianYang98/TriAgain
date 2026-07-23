package com.triagain.verification.application;

import com.triagain.verification.domain.model.Verification;
import com.triagain.verification.port.in.GetMyVerificationsUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.CrewPort.CrewPeriod;
import com.triagain.verification.port.out.VerificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetMyVerificationsService implements GetMyVerificationsUseCase {

    private final CrewPort crewPort;
    private final ChallengePort challengePort;
    private final VerificationRepositoryPort verificationRepositoryPort;
    private final Clock clock;

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

        TodaySlot todaySlot = findTodaySlot(userId, crewId);

        return new MyVerificationsResult(verifiedDates, streakCount, completedChallenges, myProgress, todaySlot);
    }

    /** 오늘 슬롯의 활성(비CANCELLED) 인증 조회 — FE가 남은 수정/취소 횟수를 안내하는 데 사용(impl-guards G-18) */
    private TodaySlot findTodaySlot(String userId, String crewId) {
        Optional<Verification> today = verificationRepositoryPort
                .findActiveByUserIdAndCrewIdAndTargetDate(userId, crewId, LocalDate.now(clock));
        return today.map(v -> new TodaySlot(v.getId(), v.getSlotAttempt())).orElse(null);
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
