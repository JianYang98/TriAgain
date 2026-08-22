package com.triagain.verification.port.out;

import java.util.List;
import java.util.Map;

public interface ReactionPort {

	/** 인증 다건의 리액션 요약 배치 조회 — support BC에 위임, 노출 상태(APPROVED) 인증만 */
	Map<String, List<ReactionSummary>> findSummaries(List<String> verificationIds, String viewerId);

	/** 이모지별 요약 — reactedByMe는 요청자 기준 */
	record ReactionSummary(String emojiType, int count, boolean reactedByMe, List<ReactionUser> users) {
	}

	/** 리액션을 남긴 사람 — 크루 정원 10명이라 전량 반환 */
	record ReactionUser(String userId, String nickname) {
	}
}
