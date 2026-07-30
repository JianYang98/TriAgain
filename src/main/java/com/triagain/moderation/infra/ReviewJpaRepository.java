package com.triagain.moderation.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewJpaRepository extends JpaRepository<ReviewJpaEntity, String> {

	Optional<ReviewJpaEntity> findByReportId(String reportId);
}
