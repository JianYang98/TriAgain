package com.triagain.verification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateUploadSessionRequest(
        @NotBlank String challengeId,
        @NotBlank String fileName,
        @NotBlank String fileType,
        @Positive long fileSize
) {}
