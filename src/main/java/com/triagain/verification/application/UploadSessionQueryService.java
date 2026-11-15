package com.triagain.verification.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
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

	/** 본인 소유 세션 조회 — 없거나 타인 소유면 V004. 폴링·SSE 구독이 공유하는 소유권 관문 */
	@Override
	@Transactional(readOnly = true)
	public UploadSessionSnapshot getOwnedOrThrow(Long id, String userId) {
		return uploadSessionRepositoryPort.findByIdAndUserId(id, userId)
				.map(this::toSnapshot)
				.orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));
	}

	private UploadSessionSnapshot toSnapshot(UploadSession session) {
		return new UploadSessionSnapshot(
				session.getId(),
				session.getCrewId(),
				session.getHabitId(),
				session.getStatus(),
				session.getRequestedAt(),
				session.getImageKey()
		);
	}
}
