package com.triagain.crew.api;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.crew.port.in.CreateCrewUseCase;
import com.triagain.crew.port.in.CreateCrewUseCase.CreateCrewCommand;
import com.triagain.crew.port.in.CreateCrewUseCase.CreateCrewResult;
import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase;
import com.triagain.crew.port.in.GetCrewByInviteCodeUseCase.CrewInvitePreviewResult;
import com.triagain.crew.port.in.GetCrewUseCase;
import com.triagain.crew.port.in.GetCrewUseCase.CrewDetailResult;
import com.triagain.crew.port.in.GetMyCrewsUseCase;
import com.triagain.crew.port.in.GetMyCrewsUseCase.CrewSummaryResult;
import com.triagain.crew.port.in.JoinCrewByInviteCodeUseCase;
import com.triagain.crew.port.in.JoinCrewByInviteCodeUseCase.JoinByInviteCodeCommand;
import com.triagain.crew.port.in.JoinCrewByInviteCodeUseCase.JoinByInviteCodeResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/crews")
@RequiredArgsConstructor
public class CrewController {

    private final CreateCrewUseCase createCrewUseCase;
    private final JoinCrewByInviteCodeUseCase joinCrewByInviteCodeUseCase;
    private final GetMyCrewsUseCase getMyCrewsUseCase;
    private final GetCrewUseCase getCrewUseCase;
    private final GetCrewByInviteCodeUseCase getCrewByInviteCodeUseCase;

    /** 크루 생성 API — POST /api/crews */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateCrewResult>> createCrew(
            @AuthenticatedUser String userId,
            @Valid @RequestBody CreateCrewRequest request
    ) {

        CreateCrewCommand command = new CreateCrewCommand(
                userId,
                request.name(),
                request.goal(),
                request.verificationContent(),
                request.verificationType(),
                request.maxMembers(),
                request.startDate(),
                request.endDate(),
                request.allowLateJoin(),
                request.deadlineTime()
        );

        CreateCrewResult result = createCrewUseCase.createCrew(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** 초대코드로 크루 참여 API — POST /api/crews/join */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinByInviteCodeResult>> joinByInviteCode(
            @AuthenticatedUser String userId,
            @Valid @RequestBody JoinCrewRequest request
    ) {
        JoinByInviteCodeCommand command = new JoinByInviteCodeCommand(userId, request.inviteCode());

        JoinByInviteCodeResult result = joinCrewByInviteCodeUseCase.joinByInviteCode(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** 내 크루 목록 조회 API — GET /api/crews */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CrewSummaryResult>>> getMyCrews(
            @AuthenticatedUser String userId
    ) {
        List<CrewSummaryResult> result = getMyCrewsUseCase.getMyCrews(userId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 초대코드로 크루 미리보기 API — GET /api/crews/invite/{inviteCode} */
    @GetMapping("/invite/{inviteCode}")
    public ResponseEntity<ApiResponse<CrewInvitePreviewResult>> getCrewByInviteCode(
            @AuthenticatedUser String userId,
            @PathVariable String inviteCode
    ) {
        CrewInvitePreviewResult result = getCrewByInviteCodeUseCase.getByInviteCode(inviteCode, userId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 크루 상세 조회 API — GET /api/crews/{crewId} */
    @GetMapping("/{crewId}")
    public ResponseEntity<ApiResponse<CrewDetailResult>> getCrew(
            @AuthenticatedUser String userId,
            @PathVariable String crewId
    ) {
        CrewDetailResult result = getCrewUseCase.getCrew(crewId, userId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
