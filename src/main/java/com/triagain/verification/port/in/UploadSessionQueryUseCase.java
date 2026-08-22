package com.triagain.verification.port.in;

import java.time.LocalDateTime;
import java.util.Optional;

import com.triagain.verification.domain.vo.UploadSessionStatus;

/** 업로드 세션 조회를 다른 컨텍스트에 노출 — habit BC가 솔로 인증 시 세션 검증(V004/V016/HB009/V005/V006/V002)에 사용 */
public interface UploadSessionQueryUseCase {

	Optional<UploadSessionSnapshot> findByIdAndUserId(Long id, String userId);

	/** 본인 소유 세션 조회 — 없거나 타인 소유면 V004. 폴링·SSE 구독이 공유하는 소유권 관문 */
	UploadSessionSnapshot getOwnedOrThrow(Long id, String userId);

	record UploadSessionSnapshot(
			Long id,
			String crewId,
			String habitId,
			UploadSessionStatus status,
			LocalDateTime requestedAt,
			String imageKey
	) {
		public boolean pending() {
			return status == UploadSessionStatus.PENDING;
		}

		public boolean completed() {
			return status == UploadSessionStatus.COMPLETED;
		}
	}
}
