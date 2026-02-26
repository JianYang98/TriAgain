package com.triagain.crew.api;

import com.triagain.common.response.ApiResponse;
import com.triagain.crew.port.in.CreateCrewUseCase;
import com.triagain.crew.port.in.CreateCrewUseCase.CreateCrewCommand;
import com.triagain.crew.port.in.CreateCrewUseCase.CreateCrewResult;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.crew.domain.model.Crew;

import java.util.List;

@RestController
@RequestMapping("/crews")
@RequiredArgsConstructor
public class CrewController {

    private final CreateCrewUseCase createCrewUseCase;
    private final JoinCrewByInviteCodeUseCase joinCrewByInviteCodeUseCase;
    private final GetMyCrewsUseCase getMyCrewsUseCase;
    private final GetCrewUseCase getCrewUseCase;

    /** 크루 생성 API — POST /api/crews */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateCrewResult>> createCrew(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateCrewRequest request
    ) {

        CreateCrewCommand command = new CreateCrewCommand(
                userId,
                request.name(),
                request.goal(),
                request.verificationType(),
                request.maxMembers(),
                request.startDate(),
                request.endDate(),
                request.allowLateJoin()
        );

        CreateCrewResult result = createCrewUseCase.createCrew(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** 초대코드로 크루 참여 API — POST /api/crews/join */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinByInviteCodeResult>> joinByInviteCode(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody JoinCrewRequest request
    ) {
        JoinByInviteCodeCommand command = new JoinByInviteCodeCommand(userId, request.inviteCode());

        JoinByInviteCodeResult result = joinCrewByInviteCodeUseCase.joinByInviteCode(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** 내 크루 목록 조회 API — GET /api/crews */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CrewSummaryResult>>> getMyCrews(
            @RequestHeader("X-User-Id") String userId
    ) {
        List<CrewSummaryResult> result = getMyCrewsUseCase.getMyCrews(userId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 크루 상세 조회 API — GET /api/crews/{crewId} */
    @GetMapping("/{crewId}")
    public ResponseEntity<ApiResponse<CrewDetailResult>> getCrew(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String crewId
    ) {
        CrewDetailResult result = getCrewUseCase.getCrew(crewId, userId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
