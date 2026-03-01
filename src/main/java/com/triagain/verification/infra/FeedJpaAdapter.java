package com.triagain.verification.infra;

import com.triagain.verification.port.out.FeedQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FeedJpaAdapter implements FeedQueryPort {

    private final FeedJpaRepository feedJpaRepository;

    /** 크루 피드 인증 목록 조회 — FeedJpaRepository에 위임 */
    @Override
    public List<FeedVerificationRow> findFeedByCrewId(String crewId, int offset, int limit) {
        return feedJpaRepository.findFeedByCrewId(crewId, offset, limit);
    }
}
