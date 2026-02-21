package com.triagain.moderation.port.out;

import com.triagain.moderation.domain.model.Review;

import java.util.Optional;

public interface ReviewRepositoryPort {

    Review save(Review review);

    Optional<Review> findById(String id);

    Optional<Review> findByReportId(String reportId);
}
