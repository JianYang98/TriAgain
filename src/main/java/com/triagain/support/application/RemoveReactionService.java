package com.triagain.support.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.support.port.in.GetReactionSummariesUseCase;
import com.triagain.support.port.in.GetReactionSummariesUseCase.ReactionSummary;
import com.triagain.support.port.in.GetReactionSummariesUseCase.VerificationReactions;
import com.triagain.support.port.in.RemoveReactionUseCase;
import com.triagain.support.port.out.CrewMembershipPort;
import com.triagain.support.port.out.ReactionRepositoryPort;
import com.triagain.support.port.out.VerificationLookupPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RemoveReactionService implements RemoveReactionUseCase {

	private final VerificationLookupPort verificationLookupPort;
	private final CrewMembershipPort crewMembershipPort;
	private final ReactionRepositoryPort reactionRepositoryPort;
	private final GetReactionSummariesUseCase getReactionSummariesUseCase;

	/** 인증에 남긴 내 리액션 제거 — 멱등(0행 삭제도 200), CANCELLED 인증도 허용(E1-d) */
	@Override
	@Transactional
	public VerificationReactions removeReaction(RemoveReactionCommand command) {
		String crewId = verificationLookupPort.findCrewIdById(command.verificationId())
				.orElseThrow(() -> new BusinessException(ErrorCode.REACTION_TARGET_NOT_FOUND));
		crewMembershipPort.validateMembership(crewId, command.userId());

		reactionRepositoryPort.deleteByVerificationIdAndUserId(command.verificationId(), command.userId());

		return toVerificationReactions(command.verificationId(), command.userId());
	}

	/** 갱신된 리액션 요약을 응답 바디로 조립 */
	private VerificationReactions toVerificationReactions(String verificationId, String viewerId) {
		List<ReactionSummary> reactions = getReactionSummariesUseCase
				.getSummaries(List.of(verificationId), viewerId)
				.getOrDefault(verificationId, List.of());
		return new VerificationReactions(verificationId, reactions);
	}
}
