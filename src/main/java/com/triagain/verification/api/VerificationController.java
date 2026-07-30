package com.triagain.verification.api;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.CancelVerificationUseCase;
import com.triagain.verification.port.in.CancelVerificationUseCase.CancelResult;
import com.triagain.verification.port.in.CreateVerificationUseCase;
import com.triagain.verification.port.in.CreateVerificationUseCase.CreateVerificationCommand;
import com.triagain.verification.port.in.CreateVerificationUseCase.VerificationResult;
import com.triagain.verification.port.in.UpdateVerificationUseCase;
import com.triagain.verification.port.in.UpdateVerificationUseCase.UpdateCommand;
import com.triagain.verification.port.in.UpdateVerificationUseCase.UpdateResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VerificationController {

	private final CreateVerificationUseCase createVerificationUseCase;
	private final CancelVerificationUseCase cancelVerificationUseCase;
	private final UpdateVerificationUseCase updateVerificationUseCase;

	/** 인증 제출 — 챌린지에 대한 텍스트/사진 인증을 생성 */
	@PostMapping("/verifications")
	public ResponseEntity<ApiResponse<VerificationResult>> createVerification(
			@AuthenticatedUser String userId,
			@Valid @RequestBody CreateVerificationRequest request
	) {
		if (request.challengeId() == null && request.crewId() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		CreateVerificationCommand command = new CreateVerificationCommand(
				userId,
				request.challengeId(),
				request.crewId(),
				request.uploadSessionId(),
				request.textContent()
		);

		VerificationResult result = createVerificationUseCase.createVerification(command);

		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}

	/** 인증 취소 — 마감 전 유저가 스스로 취소. 이미 취소된 대상에 재요청하면 멱등하게 200 반환 */
	@DeleteMapping("/verifications/{verificationId}")
	public ResponseEntity<ApiResponse<CancelResult>> cancelVerification(
			@AuthenticatedUser String userId,
			@PathVariable String verificationId
	) {
		CancelResult result = cancelVerificationUseCase.cancelVerification(verificationId, userId);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	/** 인증 수정 — 마감 전 텍스트/사진 교체(치환). 응답의 새 verificationId로 클라이언트 상태를 교체해야 한다 */
	@PatchMapping("/verifications/{verificationId}")
	public ResponseEntity<ApiResponse<UpdateResult>> updateVerification(
			@AuthenticatedUser String userId,
			@PathVariable String verificationId,
			@Valid @RequestBody UpdateVerificationRequest request
	) {
		UpdateCommand command = new UpdateCommand(
				verificationId,
				userId,
				request.uploadSessionId(),
				request.textContent()
		);

		UpdateResult result = updateVerificationUseCase.updateVerification(command);

		return ResponseEntity.ok(ApiResponse.ok(result));
	}
}
