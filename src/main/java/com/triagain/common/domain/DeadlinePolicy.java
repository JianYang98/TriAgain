package com.triagain.common.domain;

import java.time.Clock;
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
		return todayDeadline(deadlineTime, Clock.systemDefaultZone());
	}

	/** Clock 지정 오버로드 — 테스트에서 고정 시각 사용 */
	public static LocalDateTime todayDeadline(LocalTime deadlineTime, Clock clock) {
		return deadlineFor(LocalDate.now(clock), deadlineTime);
	}

	/** 특정 날짜의 마감 시각 계산 — deadlineTime null이면 기본값 23:59:59 */
	public static LocalDateTime deadlineFor(LocalDate date, LocalTime deadlineTime) {
		LocalTime time = (deadlineTime != null)
				? deadlineTime
				: LocalTime.of(23, 59, 59);
		return date.atTime(time);
	}

	/** 슬롯 유효 상한 = min(슬롯 일일마감, 사이클 마감) — endDate 캡 보존 */
	public static LocalDateTime effectiveSlotDeadline(LocalDate slot, LocalTime deadlineTime, LocalDateTime cycleDeadline) {
		LocalDateTime slotDeadline = deadlineFor(slot, deadlineTime);
		return slotDeadline.isBefore(cycleDeadline) ? slotDeadline : cycleDeadline;
	}

	/** 슬롯(챌린지의 미인증 당일) 산출 = startDate + completedDays */
	public static LocalDate slotFor(LocalDate startDate, int completedDays) {
		return startDate.plusDays(completedDays);
	}

	/** 취소 가능 상한 = 슬롯 유효상한 − 컷오프 (grace 미포함, 취소 전용 G2) */
	public static LocalDateTime cancelDeadline(LocalDateTime effectiveSlotDeadline, int cutoffMinutes) {
		return effectiveSlotDeadline.minusMinutes(cutoffMinutes);
	}
}