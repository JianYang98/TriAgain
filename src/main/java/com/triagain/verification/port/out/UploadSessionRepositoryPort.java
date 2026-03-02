package com.triagain.verification.port.out;

import com.triagain.verification.domain.model.UploadSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadSessionRepositoryPort {

    UploadSession save(UploadSession uploadSession);

    Optional<UploadSession> findById(Long id);

    Optional<UploadSession> findByIdAndUserId(Long id, String userId);

    /** PENDING 상태이고 생성 시각이 threshold 이전인 세션 목록 조회 — 만료 처리용 */
    List<UploadSession> findPendingSessionsBefore(LocalDateTime threshold);
}
