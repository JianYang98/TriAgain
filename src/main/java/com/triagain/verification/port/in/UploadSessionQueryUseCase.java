package com.triagain.verification.port.in;

import java.time.LocalDateTime;
import java.util.Optional;

/** 업로드 세션 조회를 다른 컨텍스트에 노출 — habit BC가 솔로 인증 시 세션 검증(V004/V016/HB009/V005/V006/V002)에 사용 */
public interface UploadSessionQueryUseCase {

	Optional<UploadSessionSnapshot> findByIdAndUserId(Long id, String userId);

	record UploadSessionSnapshot(
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
