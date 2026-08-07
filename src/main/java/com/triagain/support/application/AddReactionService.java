package com.triagain.support.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.util.IdGenerator;
import com.triagain.support.domain.vo.EmojiType;
import com.triagain.support.port.in.AddReactionUseCase;
import com.triagain.support.port.in.GetReactionSummariesUseCase;
import com.triagain.support.port.in.GetReactionSummariesUseCase.ReactionSummary;
import com.triagain.support.port.in.GetReactionSummariesUseCase.VerificationReactions;
import com.triagain.support.port.out.CrewMembershipPort;
import com.triagain.support.port.out.ReactionRepositoryPort;
import com.triagain.support.port.out.VerificationLookupPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddReactionService implements AddReactionUseCase {

	private final VerificationLookupPort verificationLookupPort;
	private final CrewMembershipPort crewMembershipPort;
	private final ReactionRepositoryPort reactionRepositoryPort;
	private final GetReactionSummariesUseCase getReactionSummariesUseCase;

	/** 인증에 리액션 추가 — 유저당 인증 1개, 재요청은 이모지 교체(upsert) */
	@Override
	@Transactional
	public VerificationReactions addReaction(AddReactionCommand command) {
		EmojiType emojiType = EmojiType.requireActive(command.emojiType());
		String crewId = verificationLookupPort.findCrewIdById(command.verificationId())
				.orElseThrow(() -> new BusinessException(ErrorCode.REACTION_TARGET_NOT_FOUND));
		crewMembershipPort.validateMembership(crewId, command.userId());

		// 멤버십 검사와 upsert 사이에 탈퇴가 커밋되는 경합은 잠그지 않는다(2026-08-07 PR #141 리뷰에서 재지적, 기각).
		// 그 경합의 결과가 합법적인 순서와 **구분 불가능**하기 때문이다:
		//   경합 → 리액션 저장 → 탈퇴 → 피드에 남음 / 정상 → 리액션 남김 → 탈퇴 → 피드에 남음
		// E5("크루를 떠나도 남긴 좋아요는 유지된다", 인수 시나리오 8)가 후자를 명시적으로 허용하므로
		// 막을 잘못된 상태가 없다. upsert SQL 에 crew_members 조건을 넣는 대가(Support BC 가 Crew 테이블을
		// 직접 읽음)만 남는다. 피드 조회도 같은 비잠금 검증을 쓴다(GetCrewFeedService:32).
		upsertOrThrow(command, emojiType);

		return toVerificationReactions(command.verificationId(), command.userId());
	}

	/** 조건부 원자 upsert — 영향 0행이면 인증 부재/비노출(APPROVED 아님) */
	private void upsertOrThrow(AddReactionCommand command, EmojiType emojiType) {
		int affected = reactionRepositoryPort.upsertIfVerificationActive(
				IdGenerator.generate("RCTN"),
				command.verificationId(),
				command.userId(),
				emojiType.name(),
				LocalDateTime.now());
		if (affected == 0) {
			throw new BusinessException(ErrorCode.REACTION_TARGET_NOT_FOUND);
		}
	}

	/** 갱신된 리액션 요약을 응답 바디로 조립 */
	private VerificationReactions toVerificationReactions(String verificationId, String viewerId) {
		List<ReactionSummary> reactions = getReactionSummariesUseCase
				.getSummaries(List.of(verificationId), viewerId)
				.getOrDefault(verificationId, List.of());
		return new VerificationReactions(verificationId, reactions);
	}
}
