package com.triagain.crew.api;

import jakarta.validation.constraints.Size;

import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewVisibility;

public record EditCrewRequest(
		@Size(max = 50) String name,
		@Size(max = 500) String goal,
		@Size(max = 50) String verificationContent,
		CrewCategory category,
		CrewVisibility visibility) {}
