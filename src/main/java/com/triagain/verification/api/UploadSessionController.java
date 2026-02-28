package com.triagain.verification.api;

import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.CreateUploadSessionUseCase;
import com.triagain.verification.port.in.CreateUploadSessionUseCase.CreateUploadSessionCommand;
import com.triagain.verification.port.in.CreateUploadSessionUseCase.UploadSessionResult;
import com.triagain.verification.port.out.SsePort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class UploadSessionController {

    private final CreateUploadSessionUseCase createUploadSessionUseCase;
    private final SsePort ssePort;

    @PostMapping("/upload-sessions")
    public ResponseEntity<ApiResponse<UploadSessionResult>> createUploadSession(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateUploadSessionRequest request
    ) {
        CreateUploadSessionCommand command = new CreateUploadSessionCommand(
                userId,
                request.challengeId(),
                request.fileName(),
                request.fileType(),
                request.fileSize()
        );

        UploadSessionResult result = createUploadSessionUseCase.createUploadSession(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /** SSE 구독 — 클라이언트가 업로드 완료 이벤트를 실시간 수신 */
    @GetMapping(value = "/upload-sessions/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(60_000L);
        ssePort.subscribe(id, emitter);
        return emitter;
    }
}
