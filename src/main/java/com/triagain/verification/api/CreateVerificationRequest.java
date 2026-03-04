package com.triagain.verification.api;

public record CreateVerificationRequest(
        String challengeId,
        String crewId,
        Long uploadSessionId,
        String textContent
) {}
