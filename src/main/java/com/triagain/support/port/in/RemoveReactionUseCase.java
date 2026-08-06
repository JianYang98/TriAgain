package com.triagain.support.port.in;

import com.triagain.support.port.in.GetReactionSummariesUseCase.VerificationReactions;

public interface RemoveReactionUseCase {

	/** 인증에 남긴 내 리액션 제거 — 멱등, 남긴 반응이 없어도 정상 처리 */
	VerificationReactions removeReaction(RemoveReactionCommand command);

	record RemoveReactionCommand(String verificationId, String userId) {
	}
}
