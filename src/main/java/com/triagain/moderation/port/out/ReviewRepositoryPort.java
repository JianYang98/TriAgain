package com.triagain.moderation.port.out;

import java.util.Optional;

import com.triagain.moderation.domain.model.Review;

public interface ReviewRepositoryPort {

	Review save(Review review);

	Optional<Review> findById(String id);

	Optional<Review> findByReportId(String reportId);
}
