package com.triagain.support.port.out;

import com.triagain.support.domain.model.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    List<Notification> findByUserId(String userId);

    long countUnreadByUserId(String userId);
}
