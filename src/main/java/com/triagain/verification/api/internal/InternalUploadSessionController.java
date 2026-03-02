package com.triagain.verification.api.internal;

import com.triagain.common.response.ApiResponse;
import com.triagain.verification.port.in.CompleteUploadSessionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/upload-sessions")
@RequiredArgsConstructor
public class InternalUploadSessionController {

    private final CompleteUploadSessionUseCase completeUploadSessionUseCase;

    /** 업로드 세션 완료 — Lambda가 S3 업로드 성공 시 호출 */
    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<Void>> complete(@PathVariable Long id) {
        completeUploadSessionUseCase.complete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
