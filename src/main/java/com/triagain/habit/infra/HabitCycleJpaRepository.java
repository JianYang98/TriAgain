package com.triagain.habit.infra;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.triagain.habit.domain.vo.HabitCycleStatus;

public interface HabitCycleJpaRepository extends JpaRepository<HabitCycleJpaEntity, String> {

	/** 습관·상태로 사이클 조회 — 활성(IN_PROGRESS) 사이클 확인에 사용 */
	Optional<HabitCycleJpaEntity> findByHabitIdAndStatus(String habitId, HabitCycleStatus status);

	/** 습관의 최대 사이클 번호 조회 — 다음 사이클 번호 결정에 사용 */
	@Query("SELECT COALESCE(MAX(c.cycleNumber), 0) FROM HabitCycleJpaEntity c WHERE c.habitId = :habitId")
	int findMaxCycleNumber(@Param("habitId") String habitId);

	/** 습관의 SUCCESS 사이클 수 조회 — 작심삼일 달성 횟수에 사용 */
	@Query("SELECT COUNT(c) FROM HabitCycleJpaEntity c WHERE c.habitId = :habitId AND c.status = 'SUCCESS'")
	int countSuccessByHabitId(@Param("habitId") String habitId);

	/** 습관 묶음별 SUCCESS 사이클 수 집계 — 홈 목록 N+1 방지 */
	@Query("SELECT c.habitId, COUNT(c) FROM HabitCycleJpaEntity c "
			+ "WHERE c.habitId IN :habitIds AND c.status = 'SUCCESS' "
			+ "GROUP BY c.habitId")
	List<Object[]> countSuccessGroupByHabitId(@Param("habitIds") List<String> habitIds);

	/** 습관 묶음·상태로 사이클 배치 조회 — 홈 목록 activeCycle N+1 방지 */
	List<HabitCycleJpaEntity> findAllByHabitIdInAndStatus(List<String> habitIds, HabitCycleStatus status);

	/** 마감+grace 초과 + 미인증 IN_PROGRESS 사이클 조회 — 실패 판정 스케줄러(step1 §4, {@code :now} 앱 Clock 바인딩) */
	@Query(nativeQuery = true, value = """
			SELECT hc.* FROM habit_cycles hc
			JOIN habits h
				ON hc.habit_id = h.id
			WHERE hc.status = 'IN_PROGRESS'
				AND h.status = 'ACTIVE'
				AND (hc.start_date + hc.completed_days) + h.deadline_time
					+ INTERVAL '5 minutes' < :now
				AND NOT EXISTS (
					SELECT 1 FROM habit_verifications hv
					WHERE hv.habit_id = hc.habit_id
						AND hv.target_date = hc.start_date + hc.completed_days
				)
			""")
	List<HabitCycleJpaEntity> findExpiredWithoutVerification(@Param("now") LocalDateTime now);
}
