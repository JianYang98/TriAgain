package com.triagain.support.infra;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.port.out.NotificationRepositoryPort;

import lombok.RequiredArgsConstructor;

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
	public NotificationSlice findByUserId(String userId, Boolean isRead, int page, int size) {
		Slice<NotificationJpaEntity> slice = notificationJpaRepository
				.findByUserIdAndIsReadFilter(userId, isRead, PageRequest.of(page, size));
		List<Notification> notifications = slice.getContent().stream()
				.map(NotificationJpaEntity::toDomain)
				.toList();
		return new NotificationSlice(notifications, slice.hasNext());
	}

	@Override
	public long countUnreadByUserId(String userId) {
		return notificationJpaRepository.countByUserIdAndIsReadFalse(userId);
	}

	@Override
	@Transactional
	public void deleteOlderThan(LocalDateTime dateTime) {
		notificationJpaRepository.deleteByCreatedAtBefore(dateTime);
	}

	@Override
	public void deleteAllByUserId(String userId) {
		notificationJpaRepository.deleteAllByUserId(userId);
	}

	@Override
	public void markAllAsReadByUserId(String userId) {
		notificationJpaRepository.markAllAsReadByUserId(userId);
	}

	@Override
	public boolean existsCrewFirstVerificationOnDate(String crewId, LocalDate targetDate) {
		return notificationJpaRepository.existsByTypeAndTargetIdInDay(
				NotificationType.CREW_FIRST_VERIFICATION, crewId,
				targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay());
	}
}
