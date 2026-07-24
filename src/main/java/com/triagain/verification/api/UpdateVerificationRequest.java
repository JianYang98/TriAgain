package com.triagain.verification.api;

public record UpdateVerificationRequest(Long uploadSessionId, String textContent) {
}
