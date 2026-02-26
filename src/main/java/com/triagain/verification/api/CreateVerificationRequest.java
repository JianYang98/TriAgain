package com.triagain.verification.api;

import jakarta.validation.constraints.NotBlank;

public record CreateVerificationRequest(
        @NotBlank String challengeId,
        Long uploadSessionId,
        String imageUrl,
        String textContent
) {}
