package com.triagain.verification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateUploadSessionRequest(
        @NotBlank(message = "크루 ID는 필수입니다") String crewId,
        @NotBlank(message = "파일명은 필수입니다") String fileName,
        @NotBlank(message = "파일 타입은 필수입니다") String fileType,
        @Positive(message = "파일 크기는 0보다 커야 합니다") long fileSize
) {}
