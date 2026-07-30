package com.triagain.habit.infra;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitVerificationJpaRepository extends JpaRepository<HabitVerificationJpaEntity, String> {

	/** 습관·날짜로 인증 존재 여부 확인 — 오늘 중복 인증(V003)·좀비 사이클 가드(D12)에 사용 */
	boolean existsByHabitIdAndTargetDate(String habitId, LocalDate targetDate);

	/** 습관 묶음 중 특정 날짜에 인증된 습관 ID 배치 조회 — 홈 목록 todayVerified N+1 방지 */
	@Query("SELECT v.habitId FROM HabitVerificationJpaEntity v "
			+ "WHERE v.habitId IN :habitIds AND v.targetDate = :targetDate")
	List<String> findVerifiedHabitIds(
			@Param("habitIds") List<String> habitIds,
			@Param("targetDate") LocalDate targetDate);
}
