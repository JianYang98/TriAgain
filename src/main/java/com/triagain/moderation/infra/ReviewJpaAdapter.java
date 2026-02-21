package com.triagain.moderation.infra;

import com.triagain.moderation.domain.model.Review;
import com.triagain.moderation.port.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReviewJpaAdapter implements ReviewRepositoryPort {

    private final ReviewJpaRepository reviewJpaRepository;

    @Override
    public Review save(Review review) {
        ReviewJpaEntity entity = ReviewJpaEntity.fromDomain(review);
        return reviewJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Review> findById(String id) {
        return reviewJpaRepository.findById(id)
                .map(ReviewJpaEntity::toDomain);
    }

    @Override
    public Optional<Review> findByReportId(String reportId) {
        return reviewJpaRepository.findByReportId(reportId)
                .map(ReviewJpaEntity::toDomain);
    }
}
