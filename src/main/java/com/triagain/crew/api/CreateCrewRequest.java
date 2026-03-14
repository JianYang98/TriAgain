package com.triagain.crew.api;

import com.triagain.crew.domain.vo.VerificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateCrewRequest(
        @NotBlank String name,
        @NotBlank String goal,
        @NotBlank @Size(max = 50) String verificationContent,
        @NotNull VerificationType verificationType,
        @Min(1) @Max(10) int maxMembers,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean allowLateJoin,
        LocalTime deadlineTime
) {
}
