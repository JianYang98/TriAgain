package com.triagain.verification.infra;

import org.springframework.stereotype.Component;

import com.triagain.habit.port.in.ValidateHabitUploadAccessUseCase;
import com.triagain.verification.port.out.HabitPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HabitAccessAdapter implements HabitPort {

	private final ValidateHabitUploadAccessUseCase validateHabitUploadAccessUseCase;

	/** 습관 존재·소유자·활성 상태·마감 검증 — habit BC 자체 포트/서비스로 위임(crew CrewMembershipAdapter와 동일 패턴) */
	@Override
	public void validateHabitAndDeadline(String habitId, String userId) {
		validateHabitUploadAccessUseCase.validateHabitUploadAccess(habitId, userId);
	}
}
