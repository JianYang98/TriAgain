package com.triagain.habit.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;

public interface HabitCycleRepositoryPort {

	/** 사이클 저장 — 생성·상태 변경 시 사용 */
	HabitCycle save(HabitCycle cycle);

	/** ID로 사이클 조회 */
	Optional<HabitCycle> findById(String id);

	/** 습관·상태로 사이클 조회 — 활성(IN_PROGRESS) 사이클 확인에 사용 */
	Optional<HabitCycle> findByHabitIdAndStatus(String habitId, HabitCycleStatus status);

	/** 습관의 최대 사이클 번호 조회 — 다음 사이클 번호 결정에 사용 */
	int findMaxCycleNumber(String habitId);

	/** 습관의 SUCCESS 사이클 수 조회 — 작심삼일 달성 횟수(successCount)에 사용 */
	int countSuccessByHabitId(String habitId);

	/** 습관 묶음별 SUCCESS 사이클 수 배치 조회 — 홈 목록 N+1 방지 */
	Map<String, Integer> countSuccessByHabitIds(List<String> habitIds);

	/** 습관 묶음·상태로 사이클 배치 조회 — 홈 목록 activeCycle N+1 방지 */
	List<HabitCycle> findAllByHabitIdInAndStatus(List<String> habitIds, HabitCycleStatus status);

	/** 마감+grace 초과 + 미인증 IN_PROGRESS 사이클 조회 — 실패 판정 스케줄러에서 사용(step1 §4, {@code :now} 바인딩) */
	List<HabitCycle> findExpiredWithoutVerification(LocalDateTime now);

	/** 사이클 삭제 — 시작 전 취소(hard delete, HB007 가드 통과 후)에 사용 */
	void deleteById(String id);
}
