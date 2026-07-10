package com.triagain.verification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// crewId/habitId는 XOR 관계 — 크루 인증용은 crewId, 솔로 인증용은 habitId (둘 다 null/둘 다 존재는 서비스에서 C001)
public record CreateUploadSessionRequest(
        String crewId,
        String habitId,
        @NotBlank(message = "파일명은 필수입니다") String fileName,
        @NotBlank(message = "파일 타입은 필수입니다") String fileType,
        @Positive(message = "파일 크기는 0보다 커야 합니다") long fileSize
) {}
