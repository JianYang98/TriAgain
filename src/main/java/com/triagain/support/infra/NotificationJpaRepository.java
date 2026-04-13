package com.triagain.support.infra;

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
    @Modifying
    @Query("DELETE FROM NotificationJpaEntity n WHERE n.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

    /** 사용자의 안 읽은 알림 전체 읽음 처리 */
    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.isRead = true "
            + "WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") String userId);
}
