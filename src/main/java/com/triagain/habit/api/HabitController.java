package com.triagain.habit.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.habit.port.in.CreateHabitUseCase;
import com.triagain.habit.port.in.CreateHabitUseCase.CreateHabitCommand;
import com.triagain.habit.port.in.EndHabitUseCase;
import com.triagain.habit.port.in.GetArchivedHabitsUseCase;
import com.triagain.habit.port.in.GetMyHabitsUseCase;
import com.triagain.habit.port.in.PauseHabitUseCase;
import com.triagain.habit.port.in.ResumeHabitUseCase;
import com.triagain.habit.port.in.UpdateHabitNameUseCase;
import com.triagain.habit.port.in.UpdateHabitNameUseCase.UpdateHabitNameCommand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 습관 CRUD + 멈춤/재개 + 지난기록 API — 사이클/인증은 HabitCycleController·HabitVerificationController로 분리 */
@RestController
@RequestMapping("/habits")
@RequiredArgsConstructor
public class HabitController {

	private final CreateHabitUseCase createHabitUseCase;
	private final GetMyHabitsUseCase getMyHabitsUseCase;
	private final UpdateHabitNameUseCase updateHabitNameUseCase;
	private final EndHabitUseCase endHabitUseCase;
	private final GetArchivedHabitsUseCase getArchivedHabitsUseCase;
	private final PauseHabitUseCase pauseHabitUseCase;
	private final ResumeHabitUseCase resumeHabitUseCase;

	/** 습관 등록 API — POST /habits */
	@PostMapping
	public ResponseEntity<ApiResponse<HabitResponse>> createHabit(
			@AuthenticatedUser String userId,
			@Valid @RequestBody CreateHabitRequest request
	) {
		CreateHabitCommand command = new CreateHabitCommand(
				userId, request.name(), request.verificationType(), request.deadlineTime()
		);

		HabitResponse result = createHabitUseCase.createHabit(command);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}

	/** 내 습관 목록 조회 API — GET /habits */
	@GetMapping
	public ResponseEntity<ApiResponse<List<HabitListItemResponse>>> getMyHabits(
			@AuthenticatedUser String userId
	) {
		List<HabitListItemResponse> result = getMyHabitsUseCase.getMyHabits(userId);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	/** 지난기록(종료한 습관) 조회 API — GET /habits/archived */
	@GetMapping("/archived")
	public ResponseEntity<ApiResponse<List<ArchivedHabitResponse>>> getArchivedHabits(
			@AuthenticatedUser String userId
	) {
		List<ArchivedHabitResponse> result = getArchivedHabitsUseCase.getArchivedHabits(userId);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	/** 습관 이름 수정 API — PATCH /habits/{habitId} */
	@PatchMapping("/{habitId}")
	public ResponseEntity<ApiResponse<HabitResponse>> updateHabitName(
			@AuthenticatedUser String userId,
			@PathVariable String habitId,
			@Valid @RequestBody UpdateHabitNameRequest request
	) {
		UpdateHabitNameCommand command = new UpdateHabitNameCommand(userId, habitId, request.name());

		HabitResponse result = updateHabitNameUseCase.updateHabitName(command);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	/** 습관 종료 API — POST /habits/{habitId}/end (D10, 삭제 대체 · 지난기록으로 이동) */
	@PostMapping("/{habitId}/end")
	public ResponseEntity<ApiResponse<HabitResponse>> endHabit(
			@AuthenticatedUser String userId,
			@PathVariable String habitId
	) {
		HabitResponse result = endHabitUseCase.endHabit(habitId, userId);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	/** 습관 멈춤 API — POST /habits/{habitId}/pause */
	@PostMapping("/{habitId}/pause")
	public ResponseEntity<ApiResponse<HabitResponse>> pauseHabit(
			@AuthenticatedUser String userId,
			@PathVariable String habitId
	) {
		HabitResponse result = pauseHabitUseCase.pauseHabit(habitId, userId);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	/** 습관 재개 API — POST /habits/{habitId}/resume */
	@PostMapping("/{habitId}/resume")
	public ResponseEntity<ApiResponse<HabitResponse>> resumeHabit(
			@AuthenticatedUser String userId,
			@PathVariable String habitId
	) {
		HabitResponse result = resumeHabitUseCase.resumeHabit(habitId, userId);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}
}
