package com.triagain.verification.port.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface GetCrewFeedUseCase {

    /** 크루 피드 조회 — 크루원 인증 목록 + 나의 현황 반환 */
    FeedResult getCrewFeed(FeedQuery query);

    record FeedQuery(String crewId, String userId, int page, int size) {
        public FeedQuery {
            if (page < 0) page = 0;
            if (size <= 0 || size > 50) size = 20;
        }

        public int offset() {
            return page * size;
        }
    }

    record FeedResult(
            List<FeedVerification> verifications,
            MyProgress myProgress,
            boolean hasNext
    ) {
    }

    record FeedVerification(
            String id,
            String userId,
            String nickname,
            String profileImageUrl,
            String imageUrl,
            String textContent,
            LocalDate targetDate,
            LocalDateTime createdAt
    ) {
    }

    record MyProgress(
            String challengeId,
            String status,
            int completedDays,
            int targetDays
    ) {
    }
}
