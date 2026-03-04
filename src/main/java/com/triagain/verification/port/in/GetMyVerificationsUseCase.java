package com.triagain.verification.port.in;

import java.time.LocalDate;
import java.util.List;

public interface GetMyVerificationsUseCase {

    /** 내 인증 현황 조회 — 캘린더/스트릭/달성 횟수 */
    MyVerificationsResult getMyVerifications(String crewId, String userId);

    record MyProgress(String challengeId, String status, int completedDays, int targetDays) {
    }

    record MyVerificationsResult(
            List<LocalDate> verifiedDates,
            int streakCount,
            int completedChallenges,
            MyProgress myProgress
    ) {
    }
}
