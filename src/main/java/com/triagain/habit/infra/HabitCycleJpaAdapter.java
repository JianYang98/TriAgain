package com.triagain.habit.infra;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HabitCycleJpaAdapter implements HabitCycleRepositoryPort {

	private final HabitCycleJpaRepository habitCycleJpaRepository;

	@Override
	public HabitCycle save(HabitCycle cycle) {
		HabitCycleJpaEntity entity = HabitCycleJpaEntity.fromDomain(cycle);
		return habitCycleJpaRepository.save(entity).toDomain();
	}

	@Override
	public Optional<HabitCycle> findById(String id) {
		return habitCycleJpaRepository.findById(id).map(HabitCycleJpaEntity::toDomain);
	}

	@Override
	public Optional<HabitCycle> findByHabitIdAndStatus(String habitId, HabitCycleStatus status) {
		return habitCycleJpaRepository.findByHabitIdAndStatus(habitId, status).map(HabitCycleJpaEntity::toDomain);
	}

	@Override
	public int findMaxCycleNumber(String habitId) {
		return habitCycleJpaRepository.findMaxCycleNumber(habitId);
	}

	@Override
	public int countSuccessByHabitId(String habitId) {
		return habitCycleJpaRepository.countSuccessByHabitId(habitId);
	}

	@Override
	public Map<String, Integer> countSuccessByHabitIds(List<String> habitIds) {
		if (habitIds.isEmpty()) {
			return Map.of();
		}
		List<Object[]> results = habitCycleJpaRepository.countSuccessGroupByHabitId(habitIds);
		Map<String, Integer> map = new HashMap<>();
		for (Object[] row : results) {
			map.put((String) row[0], ((Long) row[1]).intValue());
		}
		return map;
	}

	@Override
	public List<HabitCycle> findAllByHabitIdInAndStatus(List<String> habitIds, HabitCycleStatus status) {
		if (habitIds.isEmpty()) {
			return List.of();
		}
		return habitCycleJpaRepository.findAllByHabitIdInAndStatus(habitIds, status).stream()
				.map(HabitCycleJpaEntity::toDomain)
				.toList();
	}

	@Override
	public List<HabitCycle> findExpiredWithoutVerification(LocalDateTime now) {
		return habitCycleJpaRepository.findExpiredWithoutVerification(now).stream()
				.map(HabitCycleJpaEntity::toDomain)
				.toList();
	}

	@Override
	public void deleteById(String id) {
		habitCycleJpaRepository.deleteById(id);
	}
}
