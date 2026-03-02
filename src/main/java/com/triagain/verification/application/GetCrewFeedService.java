package com.triagain.verification.application;

import com.triagain.verification.port.in.GetCrewFeedUseCase;
import com.triagain.verification.port.out.ChallengePort;
import com.triagain.verification.port.out.CrewPort;
import com.triagain.verification.port.out.FeedQueryPort;
import com.triagain.verification.port.out.FeedQueryPort.FeedVerificationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCrewFeedService implements GetCrewFeedUseCase {

    private final CrewPort crewPort;
    private final FeedQueryPort feedQueryPort;
    private final ChallengePort challengePort;

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

        List<FeedVerification> verifications = pageRows.stream()
                .map(this::toFeedVerification)
                .toList();

        MyProgress myProgress = challengePort.findActiveByUserIdAndCrewId(query.userId(), query.crewId())
                .map(info -> new MyProgress(info.id(), info.status(), info.completedDays(), info.targetDays()))
                .orElse(null);

        return new FeedResult(verifications, myProgress, hasNext);
    }

    private FeedVerification toFeedVerification(FeedVerificationRow row) {
        return new FeedVerification(
                row.getId(),
                row.getUserId(),
                row.getNickname(),
                row.getProfileImageUrl(),
                row.getImageUrl(),
                row.getTextContent(),
                row.getTargetDate(),
                row.getCreatedAt()
        );
    }
}
