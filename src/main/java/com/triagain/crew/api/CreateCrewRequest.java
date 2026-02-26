package com.triagain.crew.api;

import com.triagain.crew.domain.vo.VerificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCrewRequest(
        @NotBlank String name,
        @NotBlank String goal,
        @NotNull VerificationType verificationType,
        @Min(1) @Max(10) int maxMembers,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean allowLateJoin
) {
}
