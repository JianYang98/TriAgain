package com.triagain.crew.api;

import jakarta.validation.constraints.NotBlank;

public record JoinCrewRequest(@NotBlank String inviteCode) {
}
