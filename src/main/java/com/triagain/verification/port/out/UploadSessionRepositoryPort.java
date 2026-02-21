package com.triagain.verification.port.out;

import com.triagain.verification.domain.model.UploadSession;

import java.util.Optional;

public interface UploadSessionRepositoryPort {

    UploadSession save(UploadSession uploadSession);

    Optional<UploadSession> findById(Long id);

    Optional<UploadSession> findByIdAndUserId(Long id, String userId);
}
