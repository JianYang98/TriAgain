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

		// 멤버십 검사와 upsert 사이에 탈퇴가 커밋되는 경합은 잠그지 않는다(2026-08-07 리뷰에서 2회 재지적, 기각).
		//
		//        | 순서                                | 최종 상태
		//   경합 | 검사 통과 → 탈퇴 커밋 → 리액션 저장 | 비멤버의 리액션 행 존재
		//   합법 | 리액션 저장 → 탈퇴 커밋             | 비멤버의 리액션 행 존재
		//
		// 순서는 다르고 최종 상태는 같다. 같아지는 이유는 탈퇴가 하드 삭제이기 때문이다 —
		// LeaveCrewService:36 이 deleteMemberByCrewIdAndUserId 로 crew_members 행을 지우고
		// (schema.md 상 joined_at 만 있고 탈퇴 시각 컬럼이 없다) 그래서 "언제 나갔는지"가 어디에도 안 남는다.
		// 두 경우의 DB 를 나중에 들여다보면 문자 그대로 구별할 수 없다.
		//
		// ⚠️ 논지는 "E5 가 이 행동을 허용한다"가 아니다. E5("크루를 떠나도 남긴 좋아요는 유지된다",
		// 인수 시나리오 8)가 허용하는 건 **멤버였을 때 남긴 리액션의 생존**이고, 경합이 만드는 건
		// **탈퇴 직후의 생성**이다 — 규칙으로는 다르다. 논지는 경합이 만드는 **최종 상태가 E5 가 이미
		// 허용하는 최종 상태와 구분 불가능**하다는 것이다. 막아서 되돌릴 잘못된 상태가 존재하지 않는다.
		//
		// 반면 upsert SQL 에 crew_members 조건을 넣으면 Support BC 가 Crew 테이블을 직접 읽는다 —
		// 관측 가능한 차이가 0인데 BC 경계를 넘는 거래다. 피드 조회도 같은 비잠금 검증을 쓴다
		// (GetCrewFeedService:32). 테스트로 잠그지 않는다: 이건 허용하기로 한 상태이지 계약이 아니라,
		// 나중에 멤버십을 잠그기로 하면 올바른 변경이 회귀처럼 보인다.
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
