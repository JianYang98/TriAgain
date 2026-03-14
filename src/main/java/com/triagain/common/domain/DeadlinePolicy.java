package com.triagain.common.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 마감 시간 + Grace Period 정책 — 인증 및 업로드 세션 마감 검증에 사용 */
public class DeadlinePolicy {

    public static final Duration GRACE_PERIOD = Duration.ofMinutes(5);

    /** 요청 시각이 마감 + grace period 이내인지 검증 */
    public static boolean isWithinDeadline(LocalDateTime requestedAt, LocalDateTime deadline) {
        return !requestedAt.isAfter(deadline.plus(GRACE_PERIOD));
    }

    /** 크루 마감시간으로 오늘의 마감 시각 계산 — deadlineTime null이면 기본값 23:59:59 */
    public static LocalDateTime todayDeadline(LocalTime deadlineTime) {
        LocalTime time = (deadlineTime != null)
                ? deadlineTime
                : LocalTime.of(23, 59, 59);
        return LocalDate.now().atTime(time);
    }
}