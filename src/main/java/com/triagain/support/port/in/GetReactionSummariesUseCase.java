package com.triagain.support.port.in;

import java.util.List;
import java.util.Map;

/**
 * 인증 다건의 리액션 요약 조회 — 피드와 쓰기 API 응답이 공용으로 쓴다.
 * <p>
 * 응답 계약 타입(VerificationReactions·ReactionSummary·ReactionUser)을 이 인터페이스가 단독 소유한다.
 * PUT·DELETE 응답이 "동일 구조"여야 한다는 계약(step2-api §3)이라 UseCase별로 복제하면
 * 한쪽만 바뀔 때 계약이 갈라진다 — 판정 3문 전부 YES라 추출이 정답이다.
 */
public interface GetReactionSummariesUseCase {

	/** 인증 id 다건의 리액션 요약 — 노출 상태(APPROVED) 인증만, 배치 1회 조회 */
	Map<String, List<ReactionSummary>> getSummaries(List<String> verificationIds, String viewerId);

	/** 한 인증의 리액션 요약 묶음 — PUT·DELETE 응답 바디 */
	record VerificationReactions(String verificationId, List<ReactionSummary> reactions) {
	}

	/** 이모지별 요약 — reactedByMe는 요청자 기준 */
	record ReactionSummary(String emojiType, int count, boolean reactedByMe, List<ReactionUser> users) {
	}

	/** 리액션을 남긴 사람 — 크루 정원 10명이라 전량 반환 */
	record ReactionUser(String userId, String nickname) {
	}
}
