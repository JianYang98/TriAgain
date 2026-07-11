package com.triagain.habit.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.domain.DeadlinePolicy;
import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.habit.api.HabitVerificationResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.model.HabitVerification;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.in.CreateHabitVerificationUseCase;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;
import com.triagain.habit.port.out.HabitUploadSessionPort;
import com.triagain.habit.port.out.HabitUploadSessionPort.UploadSessionInfo;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

import lombok.RequiredArgsConstructor;

/** 솔로 인증 생성 — 가드 순서·에러코드는 step1 §3이 정본(D12 슬롯 가드·좀비 사이클·세션 바인딩 포함) */
@Service
@RequiredArgsConstructor
public class CreateHabitVerificationService implements CreateHabitVerificationUseCase {

	private final HabitRepositoryPort habitRepositoryPort;
	private final HabitCycleRepositoryPort habitCycleRepositoryPort;
	private final HabitVerificationRepositoryPort habitVerificationRepositoryPort;
	private final HabitUploadSessionPort habitUploadSessionPort;
	private final StoragePort storagePort;
	private final Clock clock;

	@Override
	@Transactional
	public HabitVerificationResponse createVerification(CreateHabitVerificationCommand command) {
		Habit habit = HabitAccessGuard.requireOwnedActive(
				habitRepositoryPort.findByIdForUpdate(command.habitId()), command.userId());
		HabitCycle cycle = findInProgressCycle(habit.getId());
		LocalDate targetDate = validateTimingAndSlot(cycle);

		if (habit.getVerificationType() == HabitVerificationType.PHOTO && command.uploadSessionId() == null) {
			throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
		}

		HabitVerification verification = command.uploadSessionId() != null
				? createPhotoVerification(habit, cycle, command, targetDate)
				: createTextVerification(cycle, command, targetDate);

		HabitVerification saved = saveVerification(verification);
		cycle.recordCompletion();
		HabitCycle savedCycle = habitCycleRepositoryPort.save(cycle);

		return HabitVerificationResponse.from(saved, savedCycle);
	}

	/** 활성(IN_PROGRESS) 사이클 조회 — 없으면 HB003(가드 2) */
	private HabitCycle findInProgressCycle(String habitId) {
		return habitCycleRepositoryPort.findByHabitIdAndStatus(habitId, HabitCycleStatus.IN_PROGRESS)
				.orElseThrow(() -> new BusinessException(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS));
	}

	/** 시작일 도래(HB006, 가드 3) + 기대 슬롯 일치(V002, D12, 가드 4) + 오늘 중복(V003) 검증 */
	private LocalDate validateTimingAndSlot(HabitCycle cycle) {
		LocalDate today = LocalDate.now(clock);
		if (today.isBefore(cycle.getStartDate())) {
			throw new BusinessException(ErrorCode.HABIT_CYCLE_NOT_STARTED);
		}

		LocalDate expectedSlot = cycle.getStartDate().plusDays(cycle.getCompletedDays());
		if (!today.equals(expectedSlot)) {
			throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
		}
		if (habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(cycle.getHabitId(), today)) {
			throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_EXISTS);
		}
		return today;
	}

	/** 사진 인증 생성 — 세션 소유(V004)·크루 세션(V016)·습관 바인딩(HB009)·완료(V005/V006)·마감(V002) 순서(step1 §3-5) */
	private HabitVerification createPhotoVerification(
			Habit habit, HabitCycle cycle, CreateHabitVerificationCommand command, LocalDate targetDate) {
		UploadSessionInfo session = habitUploadSessionPort
				.findByIdAndUserId(command.uploadSessionId(), command.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

		if (session.crewId() != null) {
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);
		}
		if (!habit.getId().equals(session.habitId())) {
			throw new BusinessException(ErrorCode.HABIT_UPLOAD_SESSION_MISMATCH);
		}
		if (!session.completed()) {
			if (session.pending()) {
				throw new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_COMPLETED);
			}
			throw new BusinessException(ErrorCode.UPLOAD_SESSION_EXPIRED);
		}
		if (!DeadlinePolicy.isWithinDeadline(session.requestedAt(), cycle.getDeadline())) {
			throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
		}

		String imageUrl = storagePort.getImageUrl(session.imageKey());
		return HabitVerification.createPhoto(
				cycle.getId(), habit.getId(), command.userId(),
				session.id(), imageUrl, command.textContent(),
				targetDate, cycle.getCompletedDays() + 1
		);
	}

	/** 텍스트 인증 생성 — 마감 기준시각은 주입 Clock 경유(crew 원본 bare now()와 의도적 차이, 정정5-①) */
	private HabitVerification createTextVerification(
			HabitCycle cycle, CreateHabitVerificationCommand command, LocalDate targetDate) {
		if (!DeadlinePolicy.isWithinDeadline(LocalDateTime.now(clock), cycle.getDeadline())) {
			throw new BusinessException(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
		}
		return HabitVerification.createText(
				cycle.getId(), cycle.getHabitId(), command.userId(),
				command.textContent(), targetDate, cycle.getCompletedDays() + 1
		);
	}

	/** 저장 — 유니크 제약 위반(더블탭)을 명시적으로 V003 매핑(G2, substring 매처 오작동 방지) */
	private HabitVerification saveVerification(HabitVerification verification) {
		try {
			return habitVerificationRepositoryPort.save(verification);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_EXISTS);
		}
	}
}
