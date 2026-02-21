package com.triagain.support.infra;

import com.triagain.support.domain.model.Notification;
import com.triagain.support.port.out.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationJpaAdapter implements NotificationRepositoryPort {

    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = NotificationJpaEntity.fromDomain(notification);
        return notificationJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Notification> findById(String id) {
        return notificationJpaRepository.findById(id)
                .map(NotificationJpaEntity::toDomain);
    }

    @Override
    public List<Notification> findByUserId(String userId) {
        return notificationJpaRepository.findByUserId(userId).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countUnreadByUserId(String userId) {
        return notificationJpaRepository.countByUserIdAndIsReadFalse(userId);
    }
}
