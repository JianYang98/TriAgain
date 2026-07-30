package com.triagain.support.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.triagain.support.domain.model.Notification;

public interface NotificationRepositoryPort {

	Notification save(Notification notification);

	Optional<Notification> findById(String id);

	/** 알림 페이지네이션 결과 */
	record NotificationSlice(List<Notification> notifications, boolean hasNext) {}

	/** 사용자별 알림 최신순 페이지네이션 조회 — isRead 필터 지원 (null이면 전체) */
	NotificationSlice findByUserId(String userId, Boolean isRead, int page, int size);

	long countUnreadByUserId(String userId);

	/** 지정 일시 이전 알림 일괄 삭제 — 스케줄러용 (30일 지난 알림 정리) */
	void deleteOlderThan(LocalDateTime dateTime);

	/** 사용자의 알림 전체 삭제 — Hard Delete */
	void deleteAllByUserId(String userId);

	/** 사용자의 안 읽은 알림 전체 읽음 처리 */
	void markAllAsReadByUserId(String userId);

	/** 같은 크루·같은 날 첫인증 알림 존재 여부 — fan-out 멱등 가드 */
	boolean existsCrewFirstVerificationOnDate(String crewId, LocalDate targetDate);
}
