package com.triagain.habit.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.port.out.HabitRepositoryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HabitJpaAdapter implements HabitRepositoryPort {

	private final HabitJpaRepository habitJpaRepository;

	@Override
	public Habit save(Habit habit) {
		HabitJpaEntity entity = HabitJpaEntity.fromDomain(habit);
		return habitJpaRepository.save(entity).toDomain();
	}

	@Override
	public Optional<Habit> findById(String id) {
		return habitJpaRepository.findById(id).map(HabitJpaEntity::toDomain);
	}

	@Override
	public Optional<Habit> findByIdForUpdate(String id) {
		return habitJpaRepository.findByIdForUpdate(id).map(HabitJpaEntity::toDomain);
	}

	@Override
	public List<Habit> findAllByUserIdAndStatusNot(String userId, HabitStatus excludedStatus) {
		return habitJpaRepository.findAllByUserIdAndStatusNotOrderByCreatedAtAsc(userId, excludedStatus).stream()
				.map(HabitJpaEntity::toDomain)
				.toList();
	}

	@Override
	public List<Habit> findAllByUserIdAndStatusOrderByEndedAtDesc(String userId, HabitStatus status) {
		return habitJpaRepository.findAllByUserIdAndStatusOrderByEndedAtDesc(userId, status).stream()
				.map(HabitJpaEntity::toDomain)
				.toList();
	}
}
