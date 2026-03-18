package com.triagain.crew.api;

import com.triagain.crew.domain.vo.CrewCategory;
import com.triagain.crew.domain.vo.CrewVisibility;

public record EditCrewRequest(String name, String goal, String verificationContent,
                              CrewCategory category, CrewVisibility visibility) {}
