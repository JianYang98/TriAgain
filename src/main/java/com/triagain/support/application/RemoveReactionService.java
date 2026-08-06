package com.triagain.support.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.support.port.in.GetReactionSummariesUseCase.VerificationReactions;
import com.triagain.support.port.in.RemoveReactionUseCase;

import lombok.RequiredArgsConstructor;

// [스켈레톤] 실제 로직은 후속 커밋 — RED 테스트가 단언 실패로 떨어지게 하기 위한 계약 뼈대
@Service
@RequiredArgsConstructor
public class RemoveReactionService implements RemoveReactionUseCase {

	/** 인증에 남긴 내 리액션 제거 — 스켈레톤이라 삭제 없이 빈 요약을 반환한다 */
	@Override
	@Transactional
	public VerificationReactions removeReaction(RemoveReactionCommand command) {
		return new VerificationReactions(command.verificationId(), List.of());
	}
}
