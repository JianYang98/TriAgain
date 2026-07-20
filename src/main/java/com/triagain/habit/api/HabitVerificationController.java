package com.triagain.habit.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.habit.port.in.CreateHabitVerificationUseCase;
import com.triagain.habit.port.in.CreateHabitVerificationUseCase.CreateHabitVerificationCommand;

import lombok.RequiredArgsConstructor;

/** 솔로 인증 생성 API — 습관 하위 리소스(/habits/{habitId}/verifications)로 분리, 기존 /verifications는 crew 전용 유지 */
@RestController
@RequestMapping("/habits/{habitId}/verifications")
@RequiredArgsConstructor
public class HabitVerificationController {

	private final CreateHabitVerificationUseCase createHabitVerificationUseCase;

	/** 솔로 인증 생성 API — POST /habits/{habitId}/verifications */
	@PostMapping
	public ResponseEntity<ApiResponse<HabitVerificationResponse>> createVerification(
			@AuthenticatedUser String userId,
			@PathVariable String habitId,
			@RequestBody CreateHabitVerificationRequest request
	) {
		CreateHabitVerificationCommand command = new CreateHabitVerificationCommand(
				userId, habitId, request.uploadSessionId(), request.textContent()
		);

		HabitVerificationResponse result = createHabitVerificationUseCase.createVerification(command);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}
}
