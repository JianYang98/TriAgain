package com.triagain.crew.infra;

import com.triagain.crew.domain.vo.ChallengeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChallengeJpaRepository extends JpaRepository<ChallengeJpaEntity, String> {

    /** 유저·크루·상태로 챌린지 조회 */
    Optional<ChallengeJpaEntity> findByUserIdAndCrewIdAndStatus(String userId, String crewId, ChallengeStatus status);

    /** 크루의 특정 상태 챌린지 목록 조회 */
    List<ChallengeJpaEntity> findAllByCrewIdAndStatus(String crewId, ChallengeStatus status);

    /** 크루 멤버별 SUCCESS 챌린지 수 집계 */
    @Query("SELECT c.userId, COUNT(c) FROM ChallengeJpaEntity c WHERE c.crewId = :crewId AND c.status = 'SUCCESS' GROUP BY c.userId")
    List<Object[]> countSuccessGroupByUserId(@Param("crewId") String crewId);

    /** 마감 초과 + 미인증 IN_PROGRESS 챌린지 조회 — 실패 판정 스케줄러에서 사용 */
    @Query(nativeQuery = true, value = """
            SELECT c.* FROM challenges c
            JOIN crews cr ON c.crew_id = cr.id
            WHERE c.status = 'IN_PROGRESS'
              AND cr.status = 'ACTIVE'
              AND (c.start_date + c.completed_days) + cr.deadline_time
                  + INTERVAL '5 minutes' < NOW()
              AND NOT EXISTS (
                  SELECT 1 FROM verifications v
                  WHERE v.user_id = c.user_id
                    AND v.crew_id = c.crew_id
                    AND v.target_date = c.start_date + c.completed_days
              )
            """)
    List<ChallengeJpaEntity> findExpiredWithoutVerification();

    /** 비관적 락으로 유저·크루·상태 기준 챌린지 조회 — 동시 챌린지 생성 방지 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChallengeJpaEntity c WHERE c.userId = :userId AND c.crewId = :crewId AND c.status = :status")
    Optional<ChallengeJpaEntity> findByUserIdAndCrewIdAndStatusWithLock(
            @Param("userId") String userId,
            @Param("crewId") String crewId,
            @Param("status") ChallengeStatus status);

    /** 유저·크루의 최대 사이클 번호 조회 — 다음 사이클 번호 결정 */
    @Query("SELECT COALESCE(MAX(c.cycleNumber), 0) FROM ChallengeJpaEntity c WHERE c.userId = :userId AND c.crewId = :crewId")
    int findMaxCycleNumber(@Param("userId") String userId, @Param("crewId") String crewId);

    /** 유저·크루의 SUCCESS 챌린지 수 조회 — 작심삼일 달성 횟수 */
    @Query("SELECT COUNT(c) FROM ChallengeJpaEntity c WHERE c.userId = :userId AND c.crewId = :crewId AND c.status = 'SUCCESS'")
    int countSuccessByUserIdAndCrewId(@Param("userId") String userId, @Param("crewId") String crewId);
}
