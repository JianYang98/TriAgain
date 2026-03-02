package com.triagain.verification.infra;

import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UploadSessionJpaAdapter implements UploadSessionRepositoryPort {

    private final UploadSessionJpaRepository uploadSessionJpaRepository;

    @Override
    public UploadSession save(UploadSession uploadSession) {
        UploadSessionJpaEntity entity = UploadSessionJpaEntity.fromDomain(uploadSession);
        return uploadSessionJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<UploadSession> findById(Long id) {
        return uploadSessionJpaRepository.findById(id)
                .map(UploadSessionJpaEntity::toDomain);
    }

    @Override
    public Optional<UploadSession> findByIdAndUserId(Long id, String userId) {
        return uploadSessionJpaRepository.findByIdAndUserId(id, userId)
                .map(UploadSessionJpaEntity::toDomain);
    }

    @Override
    public List<UploadSession> findPendingSessionsBefore(LocalDateTime threshold) {
        return uploadSessionJpaRepository
                .findByStatusAndCreatedAtBefore(UploadSessionStatus.PENDING, threshold)
                .stream()
                .map(UploadSessionJpaEntity::toDomain)
                .toList();
    }
}
