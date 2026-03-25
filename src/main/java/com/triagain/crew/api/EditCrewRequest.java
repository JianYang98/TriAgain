package com.triagain.crew.api;

import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewVisibility;
import jakarta.validation.constraints.Size;

public record EditCrewRequest(
        @Size(max = 50) String name,
        @Size(max = 500) String goal,
        @Size(max = 50) String verificationContent,
        CrewCategory category,
        CrewVisibility visibility) {}
