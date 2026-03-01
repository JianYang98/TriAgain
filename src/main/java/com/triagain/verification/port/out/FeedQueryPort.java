package com.triagain.verification.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface FeedQueryPort {

    /** 크루 피드 인증 목록 조회 — 페이지네이션 */
    List<FeedVerificationRow> findFeedByCrewId(String crewId, int offset, int limit);

    /** 피드 인증 행 — native query 결과 매핑용 interface projection */
    interface FeedVerificationRow {
        String getId();
        String getUserId();
        String getNickname();
        String getProfileImageUrl();
        String getImageUrl();
        String getTextContent();
        LocalDate getTargetDate();
        LocalDateTime getCreatedAt();
    }
}
