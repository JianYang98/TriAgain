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

	/**
	 * verificationId → 이모지별 요약.
	 * <p>
	 * {@code LinkedHashMap} 은 <b>삽입 순서(= 쿼리의 행 정렬 순서)를 보존</b>할 뿐이다.
	 * <b>이모지 그룹 순서는 계약이 아니다</b> — api-spec 은 {@code reactions} 를 "이모지별 그룹"이라고만
	 * 정의하고 그룹 간 순서를 어디에도 정하지 않았다. 지금 나오는 순서는 행 정렬에서 파생된 속성이다.
	 * v1 은 활성 이모지가 LIKE 하나라 그룹이 항상 1개이므로 미결이어도 무해하다.
	 * 활성 세트를 늘릴 땐 그룹 순서를 <b>먼저 계약으로 정하고</b>(enum 고정 순서 vs 최초 반응 순)
	 * api-spec 에 명시한 뒤 테스트를 붙인다 — future-considerations.md 2026-08-07 ④.
	 */
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
