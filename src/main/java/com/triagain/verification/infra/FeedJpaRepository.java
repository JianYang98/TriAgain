package com.triagain.verification.infra;

import com.triagain.verification.port.out.FeedQueryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedJpaRepository extends JpaRepository<VerificationJpaEntity, String> {

    /** 크루 피드 조회 — APPROVED 인증만 최신순, 유저 정보 JOIN */
    @Query(value = """
            SELECT v.id, v.user_id AS userId, u.nickname, u.profile_image_url AS profileImageUrl,
                   v.image_url AS imageUrl, v.text_content AS textContent,
                   v.target_date AS targetDate, v.created_at AS createdAt
            FROM verifications v JOIN users u ON v.user_id = u.id
            WHERE v.crew_id = :crewId AND v.status = 'APPROVED'
            ORDER BY v.created_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<FeedQueryPort.FeedVerificationRow> findFeedByCrewId(
            @Param("crewId") String crewId,
            @Param("offset") int offset,
            @Param("limit") int limit);
}
