package com.triagain.verification.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadSessionJpaRepository extends JpaRepository<UploadSessionJpaEntity, Long> {

    Optional<UploadSessionJpaEntity> findByIdAndUserId(Long id, String userId);
}
