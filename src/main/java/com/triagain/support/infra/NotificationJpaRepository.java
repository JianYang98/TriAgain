package com.triagain.support.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {

    /** 사용자별 알림 최신순 페이지네이션 조회 */
    Slice<NotificationJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(String userId);

    /** 지정 일시 이전 알림 삭제 */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
