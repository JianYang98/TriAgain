package com.triagain.habit.infra;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.triagain.habit.domain.vo.HabitStatus;

public interface HabitJpaRepository extends JpaRepository<HabitJpaEntity, String> {

	/** 비관적 락으로 습관 조회 — habit 뮤테이션 서비스 진입 시 셀프 경합 직렬화(D13) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT h FROM HabitJpaEntity h WHERE h.id = :id")
	Optional<HabitJpaEntity> findByIdForUpdate(@Param("id") String id);

	/** 홈 목록 조회 — 특정 상태를 제외한 본인 습관, createdAt 오름차순 */
	List<HabitJpaEntity> findAllByUserIdAndStatusNotOrderByCreatedAtAsc(String userId, HabitStatus excludedStatus);

	/** 지난기록 조회 — 특정 상태인 본인 습관, endedAt 내림차순 */
	List<HabitJpaEntity> findAllByUserIdAndStatusOrderByEndedAtDesc(String userId, HabitStatus status);
}
