package com.triagain.moderation.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewJpaRepository extends JpaRepository<ReviewJpaEntity, String> {

    Optional<ReviewJpaEntity> findByReportId(String reportId);
}
