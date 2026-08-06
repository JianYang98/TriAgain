package com.triagain.verification.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.verification.port.in.GetCrewFeedUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.FeedQueryPort;
import com.triagain.verification.port.out.FeedQueryPort.FeedVerificationRow;
import com.triagain.verification.port.out.ReactionPort;
import com.triagain.verification.port.out.ReactionPort.ReactionSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCrewFeedService implements GetCrewFeedUseCase {

	private final CrewPort crewPort;
	private final FeedQueryPort feedQueryPort;
	private final ChallengePort challengePort;
	private final ReactionPort reactionPort;

	/** 크루 피드 조회 — 멤버십 검증 후 인증 목록 + 나의 현황 반환 */
	@Override
	public FeedResult getCrewFeed(FeedQuery query) {
		crewPort.validateMembership(query.crewId(), query.userId());

		List<FeedVerificationRow> rows = feedQueryPort.findFeedByCrewId(
				query.crewId(), query.offset(), query.size() + 1);

		boolean hasNext = rows.size() > query.size();
		List<FeedVerificationRow> pageRows = hasNext
				? rows.subList(0, query.size())
				: rows;

		Map<String, List<ReactionSummary>> reactionsByVerification = fetchReactions(pageRows, query.userId());

		List<FeedVerification> verifications = pageRows.stream()
				.map(row -> toFeedVerification(row, reactionsByVerification))
				.toList();

		MyProgress myProgress = challengePort.findActiveByUserIdAndCrewId(query.userId(), query.crewId())
				.map(info -> new MyProgress(info.id(), info.status(), info.completedDays(), info.targetDays()))
				.orElse(null);

		return new FeedResult(verifications, myProgress, hasNext);
	}

	/** 페이지 내 인증 id 배치로 리액션 요약 1회 조회 — 인증 건별 쿼리(N+1) 금지 */
	private Map<String, List<ReactionSummary>> fetchReactions(List<FeedVerificationRow> pageRows, String viewerId) {
		if (pageRows.isEmpty()) {
			return Map.of();
		}
		List<String> ids = pageRows.stream().map(FeedVerificationRow::getId).toList();
		return reactionPort.findSummaries(ids, viewerId);
	}

	private FeedVerification toFeedVerification(
			FeedVerificationRow row, Map<String, List<ReactionSummary>> reactionsByVerification) {
		return new FeedVerification(
				row.getId(),
				row.getUserId(),
				row.getNickname(),
				row.getProfileImageUrl(),
				row.getImageUrl(),
				row.getTextContent(),
				row.getTargetDate(),
				row.getSlotAttempt(),
				row.getCreatedAt(),
				reactionsByVerification.getOrDefault(row.getId(), List.of())
		);
	}
}
