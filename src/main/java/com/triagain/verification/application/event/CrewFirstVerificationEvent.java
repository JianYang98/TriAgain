package com.triagain.verification.application.event;

import java.time.LocalDate;

/** 크루 오늘 첫 인증 이벤트 — 트랜잭션 커밋 후 모닝콜 fan-out에 사용 */
public record CrewFirstVerificationEvent(String firstUserId, String crewId, LocalDate targetDate) {
}
