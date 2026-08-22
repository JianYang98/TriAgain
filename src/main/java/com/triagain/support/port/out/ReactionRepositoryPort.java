package com.triagain.support.port.out;

import java.time.LocalDateTime;
import java.util.List;

public interface ReactionRepositoryPort {

	/** 리액션 upsert — 인증이 노출 상태(APPROVED)일 때만 삽입/교체. 영향 행 0이면 대상 없음/비노출 */
	int upsertIfVerificationActive(String id, String verificationId, String userId, String emoji, LocalDateTime now);

	/** 내 리액션 제거 — 멱등(0행도 정상) */
	long deleteByVerificationIdAndUserId(String verificationId, String userId);

	/** 인증 다건의 리액션 요약 행 — 피드·응답 바디 공용, 노출 상태(APPROVED) 인증만 */
	List<ReactionRow> findRowsByVerificationIdIn(List<String> verificationIds);

	/** 리액션 요약 원본 행 — native query 결과 매핑용 interface projection */
	interface ReactionRow {
		String getVerificationId();

		String getUserId();

		String getNickname();

		String getEmoji();
	}
}
