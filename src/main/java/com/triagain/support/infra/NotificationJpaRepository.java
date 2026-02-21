package com.triagain.support.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {

    List<NotificationJpaEntity> findByUserId(String userId);

    long countByUserIdAndIsReadFalse(String userId);
}
