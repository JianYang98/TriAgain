package com.triagain.habit.api;

import com.triagain.habit.domain.vo.CycleStartOption;

public record StartCycleRequest(
		CycleStartOption startOption
) {
}
