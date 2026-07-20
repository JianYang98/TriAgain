package com.triagain.habit.port.out;

import java.util.List;
import java.util.Optional;

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;

public interface HabitRepositoryPort {

	/** 습관 저장 — 생성·상태 변경 시 사용 */
	Habit save(Habit habit);

	/** ID로 습관 조회 */
	Optional<Habit> findById(String id);

	/** 비관적 락으로 습관 조회 — 모든 habit 뮤테이션 서비스 진입 시 셀프 경합 직렬화에 사용(D13). 스케줄러·부팅 보정은 미참여 */
	Optional<Habit> findByIdForUpdate(String id);

	/** 홈 목록 조회 — 특정 상태를 제외한 본인 습관, createdAt 오름차순(§2 정렬) */
	List<Habit> findAllByUserIdAndStatusNot(String userId, HabitStatus excludedStatus);

	/** 지난기록 조회 — 특정 상태인 본인 습관, endedAt 내림차순(§4-2 정렬) */
	List<Habit> findAllByUserIdAndStatusOrderByEndedAtDesc(String userId, HabitStatus status);
}
