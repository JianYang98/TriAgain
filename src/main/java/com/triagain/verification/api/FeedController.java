package com.triagain.verification.api;

import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.GetCrewFeedUseCase;
import com.triagain.verification.port.in.GetCrewFeedUseCase.FeedQuery;
import com.triagain.verification.port.in.GetCrewFeedUseCase.FeedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FeedController {

    private final GetCrewFeedUseCase getCrewFeedUseCase;

    /** 크루 피드 조회 — 크루원들의 인증 목록 + 나의 현황 */
    @GetMapping("/crews/{crewId}/feed")
    public ResponseEntity<ApiResponse<FeedResult>> getCrewFeed(
            @PathVariable String crewId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FeedResult result = getCrewFeedUseCase.getCrewFeed(new FeedQuery(crewId, userId, page, size));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
