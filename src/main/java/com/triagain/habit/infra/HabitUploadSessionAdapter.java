package com.triagain.habit.infra;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.triagain.habit.port.out.HabitUploadSessionPort;
import com.triagain.verification.port.in.UploadSessionQueryUseCase;
import com.triagain.verification.port.in.UploadSessionQueryUseCase.UploadSessionSnapshot;

import lombok.RequiredArgsConstructor;

/** verification BC의 세션 조회를 자기 포트 뒤로 위임 — crew의 CrewMembershipAdapter와 동일한 크로스 컨텍스트 연결 방식(step4 §1) */
@Component
@RequiredArgsConstructor
public class HabitUploadSessionAdapter implements HabitUploadSessionPort {

	private final UploadSessionQueryUseCase uploadSessionQueryUseCase;

	@Override
	public Optional<UploadSessionInfo> findByIdAndUserId(Long sessionId, String userId) {
		return uploadSessionQueryUseCase.findByIdAndUserId(sessionId, userId).map(this::toInfo);
	}

	private UploadSessionInfo toInfo(UploadSessionSnapshot snapshot) {
		return new UploadSessionInfo(
				snapshot.id(),
				snapshot.crewId(),
				snapshot.habitId(),
				snapshot.pending(),
				snapshot.completed(),
				snapshot.requestedAt(),
				snapshot.imageKey()
		);
	}
}
