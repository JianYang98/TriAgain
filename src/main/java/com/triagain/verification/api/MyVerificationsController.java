package com.triagain.verification.api;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.GetMyVerificationsUseCase;
import com.triagain.verification.port.in.GetMyVerificationsUseCase.MyVerificationsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MyVerificationsController {

    private final GetMyVerificationsUseCase getMyVerificationsUseCase;

    /** 내 인증 현황 조회 — 인증 날짜 + 스트릭 + 달성 횟수 */
    @GetMapping("/crews/{crewId}/my-verifications")
    public ResponseEntity<ApiResponse<MyVerificationsResult>> getMyVerifications(
            @PathVariable String crewId,
            @AuthenticatedUser String userId
    ) {
        MyVerificationsResult result = getMyVerificationsUseCase.getMyVerifications(crewId, userId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
