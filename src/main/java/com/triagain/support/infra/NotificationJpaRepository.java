package com.triagain.support.infra;

import com.triagain.support.domain.vo.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {

    /** 사용자별 알림 최신순 페이지네이션 조회 */
    Slice<NotificationJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** 사용자별 알림 최신순 + 읽음 필터 페이지네이션 조회 */
    @Query("SELECT n FROM NotificationJpaEntity n "
            + "WHERE n.userId = :userId "
            + "AND (:isRead IS NULL OR n.isRead = :isRead) "
            + "ORDER BY n.createdAt DESC")
    Slice<NotificationJpaEntity> findByUserIdAndIsReadFilter(
            @Param("userId") String userId,
            @Param("isRead") Boolean isRead,
            Pageable pageable);

    long countByUserIdAndIsReadFalse(String userId);

    /** 지정 일시 이전 알림 삭제 */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);

    /** 사용자의 알림 전체 삭제 */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NotificationJpaEntity n WHERE n.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

    /** 사용자의 안 읽은 알림 전체 읽음 처리 */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationJpaEntity n SET n.isRead = true "
            + "WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") String userId);

    /** 같은 크루·같은 날 첫인증 알림 존재 여부 — created_at 일자 범위 검사로 멱등 가드 */
    @Query("""
            SELECT COUNT(n) > 0 FROM NotificationJpaEntity n
            WHERE n.type = :type AND n.targetId = :crewId
              AND n.createdAt >= :dayStart AND n.createdAt < :dayEnd
            """)
    boolean existsByTypeAndTargetIdInDay(@Param("type") NotificationType type,
                                         @Param("crewId") String crewId,
                                         @Param("dayStart") LocalDateTime dayStart,
                                         @Param("dayEnd") LocalDateTime dayEnd);
}
