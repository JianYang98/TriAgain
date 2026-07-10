package com.triagain.verification.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.in.UploadSessionQueryUseCase;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadSessionQueryService implements UploadSessionQueryUseCase {

	private final UploadSessionRepositoryPort uploadSessionRepositoryPort;

	@Override
	@Transactional(readOnly = true)
	public Optional<UploadSessionSnapshot> findByIdAndUserId(Long id, String userId) {
		return uploadSessionRepositoryPort.findByIdAndUserId(id, userId).map(this::toSnapshot);
	}

	private UploadSessionSnapshot toSnapshot(UploadSession session) {
		return new UploadSessionSnapshot(
				session.getId(),
				session.getCrewId(),
				session.getHabitId(),
				session.isPending(),
				session.isCompleted(),
				session.getRequestedAt(),
				session.getImageKey()
		);
	}
}
