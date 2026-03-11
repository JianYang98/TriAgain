package com.triagain.verification.api.internal;

import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.CompleteUploadSessionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/upload-sessions")
@RequiredArgsConstructor
public class InternalUploadSessionController {

    private final CompleteUploadSessionUseCase completeUploadSessionUseCase;

    /** 업로드 세션 완료 — Lambda가 S3 업로드 성공 시 imageKey로 호출 */
    @PutMapping("/complete")
    public ResponseEntity<ApiResponse<Void>> complete(@RequestParam String imageKey) {
        completeUploadSessionUseCase.complete(imageKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
