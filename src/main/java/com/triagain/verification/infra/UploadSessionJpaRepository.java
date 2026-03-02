package com.triagain.verification.infra;

import com.triagain.verification.domain.vo.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadSessionJpaRepository extends JpaRepository<UploadSessionJpaEntity, Long> {

    Optional<UploadSessionJpaEntity> findByIdAndUserId(Long id, String userId);

    List<UploadSessionJpaEntity> findByStatusAndCreatedAtBefore(
            UploadSessionStatus status, LocalDateTime threshold);
}
