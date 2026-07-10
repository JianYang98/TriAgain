package com.triagain.habit.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.habit.domain.vo.CycleStartOption;
import com.triagain.habit.port.in.CancelHabitCycleUseCase;
import com.triagain.habit.port.in.StartHabitCycleUseCase;
import com.triagain.habit.port.in.StartHabitCycleUseCase.StartCycleResult;
import com.triagain.habit.port.in.StartHabitCycleUseCase.StartHabitCycleCommand;

import lombok.RequiredArgsConstructor;

/** 작심 사이클 시작/취소 API — 습관 하위 리소스(/habits/{habitId}/cycles)로 분리 */
@RestController
@RequestMapping("/habits/{habitId}/cycles")
@RequiredArgsConstructor
public class HabitCycleController {

	private final StartHabitCycleUseCase startHabitCycleUseCase;
	private final CancelHabitCycleUseCase cancelHabitCycleUseCase;

	/** 작심 사이클 시작(첫 시작/재시작 통합) API — POST /habits/{habitId}/cycles */
	@PostMapping
	public ResponseEntity<ApiResponse<HabitCycleResponse>> startCycle(
			@AuthenticatedUser String userId,
			@PathVariable String habitId,
			@RequestBody(required = false) StartCycleRequest request
	) {
		CycleStartOption startOption = request != null ? request.startOption() : null;
		StartHabitCycleCommand command = new StartHabitCycleCommand(userId, habitId, startOption);

		StartCycleResult result = startHabitCycleUseCase.startCycle(command);
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

		return ResponseEntity.status(status).body(ApiResponse.ok(result.cycle()));
	}

	/** 시작 전 사이클 취소 API — DELETE /habits/{habitId}/cycles/current */
	@DeleteMapping("/current")
	public ResponseEntity<Void> cancelCurrentCycle(
			@AuthenticatedUser String userId,
			@PathVariable String habitId
	) {
		cancelHabitCycleUseCase.cancelCurrentCycle(habitId, userId);

		return ResponseEntity.noContent().build();
	}
}
