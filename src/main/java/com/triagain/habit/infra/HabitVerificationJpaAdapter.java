package com.triagain.habit.infra;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.triagain.habit.domain.model.HabitVerification;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HabitVerificationJpaAdapter implements HabitVerificationRepositoryPort {

	private final HabitVerificationJpaRepository habitVerificationJpaRepository;

	@Override
	public HabitVerification save(HabitVerification verification) {
		HabitVerificationJpaEntity entity = HabitVerificationJpaEntity.fromDomain(verification);
		return habitVerificationJpaRepository.save(entity).toDomain();
	}

	@Override
	public boolean existsByHabitIdAndTargetDate(String habitId, LocalDate targetDate) {
		return habitVerificationJpaRepository.existsByHabitIdAndTargetDate(habitId, targetDate);
	}

	@Override
	public Set<String> findVerifiedHabitIds(List<String> habitIds, LocalDate targetDate) {
		if (habitIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(habitVerificationJpaRepository.findVerifiedHabitIds(habitIds, targetDate));
	}
}
