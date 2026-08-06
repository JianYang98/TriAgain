package com.triagain.support.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.support.port.in.GetReactionSummariesUseCase;
import com.triagain.support.port.out.ReactionRepositoryPort;
import com.triagain.support.port.out.ReactionRepositoryPort.ReactionRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetReactionSummariesService implements GetReactionSummariesUseCase {

	private final ReactionRepositoryPort reactionRepositoryPort;

	/** 인증 다건의 리액션 요약 — 노출 상태(APPROVED) 인증만, 배치 1회 조회 */
	@Override
	@Transactional(readOnly = true)
	public Map<String, List<ReactionSummary>> getSummaries(List<String> verificationIds, String viewerId) {
		List<ReactionRow> rows = reactionRepositoryPort.findRowsByVerificationIdIn(verificationIds);
		return groupByVerification(rows, viewerId);
	}

	/** verificationId → 이모지별 요약 — 이모지 그룹 순서 고정을 위해 바깥·안쪽 모두 LinkedHashMap */
	private Map<String, List<ReactionSummary>> groupByVerification(List<ReactionRow> rows, String viewerId) {
		Map<String, Map<String, List<ReactionRow>>> byVerificationThenEmoji = new LinkedHashMap<>();
		for (ReactionRow row : rows) {
			byVerificationThenEmoji
					.computeIfAbsent(row.getVerificationId(), k -> new LinkedHashMap<>())
					.computeIfAbsent(row.getEmoji(), k -> new ArrayList<>())
					.add(row);
		}

		Map<String, List<ReactionSummary>> result = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, List<ReactionRow>>> entry : byVerificationThenEmoji.entrySet()) {
			result.put(entry.getKey(), toSummaries(entry.getValue(), viewerId));
		}
		return result;
	}

	/** 이모지별 행 묶음 → ReactionSummary 리스트 (count·reactedByMe·users 계산) */
	private List<ReactionSummary> toSummaries(Map<String, List<ReactionRow>> rowsByEmoji, String viewerId) {
		List<ReactionSummary> summaries = new ArrayList<>();
		for (Map.Entry<String, List<ReactionRow>> entry : rowsByEmoji.entrySet()) {
			List<ReactionRow> emojiRows = entry.getValue();
			List<ReactionUser> users = emojiRows.stream()
					.map(row -> new ReactionUser(row.getUserId(), row.getNickname()))
					.toList();
			boolean reactedByMe = emojiRows.stream().anyMatch(row -> row.getUserId().equals(viewerId));
			summaries.add(new ReactionSummary(entry.getKey(), emojiRows.size(), reactedByMe, users));
		}
		return summaries;
	}
}
