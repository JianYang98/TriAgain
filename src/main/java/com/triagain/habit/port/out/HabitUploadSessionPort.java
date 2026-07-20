package com.triagain.habit.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * verification BC의 업로드 세션 조회를 위임 — crew의 verification.port.out.CrewPort ↔
 * crew.infra.CrewMembershipAdapter 크로스 컨텍스트 연결과 대칭(step4 §1)
 */
public interface HabitUploadSessionPort {

	/** 본인 소유 업로드 세션 조회 — 솔로 인증 생성 시 세션 검증(V004/V016/HB009/V005/V006/V002)에 사용 */
	Optional<UploadSessionInfo> findByIdAndUserId(Long sessionId, String userId);

	record UploadSessionInfo(
			Long id,
			String crewId,
			String habitId,
			boolean pending,
			boolean completed,
			LocalDateTime requestedAt,
			String imageKey
	) {
	}
}
