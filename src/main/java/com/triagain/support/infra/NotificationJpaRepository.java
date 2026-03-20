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

    long countByUserIdAndIsReadFalse(String userId);

    /** 읽음 처리 (벌크 UPDATE) */
    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.id = :id")
    void markAsRead(@Param("id") String id);

    /** 지정 일시 이전 알림 삭제 */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
