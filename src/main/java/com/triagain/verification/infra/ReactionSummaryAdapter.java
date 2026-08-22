package com.triagain.verification.infra;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.triagain.support.port.in.GetReactionSummariesUseCase;
import com.triagain.verification.port.out.ReactionPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReactionSummaryAdapter implements ReactionPort {

	private final GetReactionSummariesUseCase getReactionSummariesUseCase;

	/** 인증 다건의 리액션 요약 배치 조회 — support 타입을 verification 타입으로 변환 */
	@Override
	public Map<String, List<ReactionSummary>> findSummaries(List<String> verificationIds, String viewerId) {
		Map<String, List<GetReactionSummariesUseCase.ReactionSummary>> summariesByVerification =
				getReactionSummariesUseCase.getSummaries(verificationIds, viewerId);

		Map<String, List<ReactionSummary>> result = new LinkedHashMap<>();
		for (Map.Entry<String, List<GetReactionSummariesUseCase.ReactionSummary>> entry
				: summariesByVerification.entrySet()) {
			result.put(entry.getKey(), toReactionSummaries(entry.getValue()));
		}
		return result;
	}

	private List<ReactionSummary> toReactionSummaries(List<GetReactionSummariesUseCase.ReactionSummary> source) {
		return source.stream()
				.map(s -> new ReactionSummary(s.emojiType(), s.count(), s.reactedByMe(), toReactionUsers(s.users())))
				.toList();
	}

	private List<ReactionUser> toReactionUsers(List<GetReactionSummariesUseCase.ReactionUser> users) {
		return users.stream()
				.map(u -> new ReactionUser(u.userId(), u.nickname()))
				.toList();
	}
}
