package com.triagain.verification.api;

import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.CreateUploadSessionUseCase;
import com.triagain.verification.port.in.CreateUploadSessionUseCase.CreateUploadSessionCommand;
import com.triagain.verification.port.in.CreateUploadSessionUseCase.UploadSessionResult;
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
public class UploadSessionController {

    private final CreateUploadSessionUseCase createUploadSessionUseCase;

    @PostMapping("/upload-sessions")
    public ResponseEntity<ApiResponse<UploadSessionResult>> createUploadSession(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateUploadSessionRequest request
    ) {
        CreateUploadSessionCommand command = new CreateUploadSessionCommand(
                userId,
                request.fileName(),
                request.fileType(),
                request.fileSize()
        );

        UploadSessionResult result = createUploadSessionUseCase.createUploadSession(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }
}
