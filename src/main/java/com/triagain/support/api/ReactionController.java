package com.triagain.support.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.auth.AuthenticatedUser;
import com.triagain.common.response.ApiResponse;
import com.triagain.support.port.in.AddReactionUseCase;
import com.triagain.support.port.in.GetReactionSummariesUseCase.VerificationReactions;
import com.triagain.support.port.in.RemoveReactionUseCase;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/verifications/{verificationId}/reactions")
@RequiredArgsConstructor
public class ReactionController {

	private final AddReactionUseCase addReactionUseCase;
	private final RemoveReactionUseCase removeReactionUseCase;

	/** 인증에 리액션 추가 — 유저당 인증 1개, 재요청은 이모지 교체 */
	@PutMapping
	public ResponseEntity<ApiResponse<VerificationReactions>> addReaction(
			@AuthenticatedUser String userId,
			@PathVariable String verificationId,
			@Valid @RequestBody ReactionRequest request) {
		VerificationReactions result = addReactionUseCase.addReaction(
				new AddReactionUseCase.AddReactionCommand(verificationId, userId, request.emojiType()));
		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	/** 인증에 남긴 내 리액션 제거 — 멱등, 없어도 200 */
	@DeleteMapping
	public ResponseEntity<ApiResponse<VerificationReactions>> removeReaction(
			@AuthenticatedUser String userId,
			@PathVariable String verificationId) {
		VerificationReactions result = removeReactionUseCase.removeReaction(
				new RemoveReactionUseCase.RemoveReactionCommand(verificationId, userId));
		return ResponseEntity.ok(ApiResponse.ok(result));
	}
}
