package com.triagain.verification.api;

import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.CreateVerificationUseCase;
import com.triagain.verification.port.in.CreateVerificationUseCase.CreateVerificationCommand;
import com.triagain.verification.port.in.CreateVerificationUseCase.VerificationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VerificationController {

    private final CreateVerificationUseCase createVerificationUseCase;

    /** 인증 제출 — 챌린지에 대한 텍스트/사진 인증을 생성 */
    @PostMapping("/verifications")
    public ResponseEntity<ApiResponse<VerificationResult>> createVerification(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateVerificationRequest request
    ) {
        CreateVerificationCommand command = new CreateVerificationCommand(
                userId,
                request.challengeId(),
                request.uploadSessionId(),
                request.imageUrl(),
                request.textContent()
        );

        VerificationResult result = createVerificationUseCase.createVerification(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }
}
