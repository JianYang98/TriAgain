package com.triagain.moderation.domain.model;

import java.time.LocalDateTime;

public class ReportPolicy {

    public static final int REPORT_THRESHOLD = 3;
    public static final int REVIEW_EXPIRY_DAYS = 7;

    private ReportPolicy() {
    }

    public static boolean shouldTriggerReview(int reportCount) {
        return reportCount >= REPORT_THRESHOLD;
    }

    public static boolean isExpired(LocalDateTime createdAt) {
        return LocalDateTime.now().isAfter(createdAt.plusDays(REVIEW_EXPIRY_DAYS));
    }
}
