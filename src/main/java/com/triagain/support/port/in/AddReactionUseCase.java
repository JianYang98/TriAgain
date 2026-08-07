package com.triagain.support.port.in;

import com.triagain.support.port.in.GetReactionSummariesUseCase.VerificationReactions;

public interface AddReactionUseCase {

	/** 인증에 리액션 추가 — 유저당 인증 1개, 재요청은 이모지 교체(멱등) */
	VerificationReactions addReaction(AddReactionCommand command);

	record AddReactionCommand(String verificationId, String userId, String emojiType) {
	}
}
