package com.triagain.habit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.port.out.StoragePort;
import com.triagain.habit.api.HabitVerificationResponse;
import com.triagain.habit.domain.model.Habit;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;
import com.triagain.habit.port.in.CreateHabitVerificationUseCase.CreateHabitVerificationCommand;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;
import com.triagain.habit.port.out.HabitRepositoryPort;
import com.triagain.habit.port.out.HabitUploadSessionPort;
import com.triagain.habit.port.out.HabitUploadSessionPort.UploadSessionInfo;
import com.triagain.habit.port.out.HabitVerificationRepositoryPort;

@ExtendWith(MockitoExtension.class)
class CreateHabitVerificationServiceTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 5, 14, 0, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW.atZone(ZONE).toInstant(), ZONE);
	private static final LocalDate TODAY = FIXED_NOW.toLocalDate();

	private static final String USER_ID = "user-1";
	private static final String HABIT_ID = "habit-1";
	private static final String CYCLE_ID = "HCYC-1";

	@Mock
	private HabitRepositoryPort habitRepositoryPort;

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	@Mock
	private HabitVerificationRepositoryPort habitVerificationRepositoryPort;

	@Mock
	private HabitUploadSessionPort habitUploadSessionPort;

	@Mock
	private StoragePort storagePort;

	private CreateHabitVerificationService service;

	@BeforeEach
	void setUp() {
		service = new CreateHabitVerificationService(
				habitRepositoryPort, habitCycleRepositoryPort, habitVerificationRepositoryPort,
				habitUploadSessionPort, storagePort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("TEXT 습관 정상 인증 — completedDays+1, IN_PROGRESS 유지")
	void textVerification_success() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitVerificationRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitVerificationResponse result = service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "오늘도 물 2L"));

		// Then
		assertThat(result.cycle().completedDays()).isEqualTo(1);
		assertThat(result.cycle().status()).isEqualTo(HabitCycleStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("3일차 인증 — 사이클이 SUCCESS로 전환된다")
	void thirdDayVerification_success() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY.minusDays(2), 2, FIXED_NOW.plusMinutes(30));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitVerificationRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitVerificationResponse result = service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "오늘도 물 2L"));

		// Then
		assertThat(result.cycle().completedDays()).isEqualTo(3);
		assertThat(result.cycle().status()).isEqualTo(HabitCycleStatus.SUCCESS);
	}

	@Test
	@DisplayName("오늘 중복 인증 — VERIFICATION_ALREADY_EXISTS(V003)")
	void duplicateToday_throws() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(true);

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ALREADY_EXISTS);
	}

	@Test
	@DisplayName("시작일 도래 전(TOMORROW 사이클) — HABIT_CYCLE_NOT_STARTED(HB006)")
	void beforeStartDate_throws() {
		// Given — 사이클 시작일이 내일
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY.plusDays(1), 0, FIXED_NOW.plusDays(4));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_CYCLE_NOT_STARTED);
	}

	@Test
	@DisplayName("PAUSED 습관 인증 시도 — HABIT_NOT_ACTIVE(HB008, 가드 1b)")
	void pausedHabit_throws() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.PAUSED);

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("FAILED 사이클(활성 사이클 없음) — HABIT_CYCLE_NOT_IN_PROGRESS(HB003)")
	void noActiveCycle_throws() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_CYCLE_NOT_IN_PROGRESS);
	}

	@Test
	@DisplayName("PHOTO 습관인데 세션 없음 — PHOTO_REQUIRED(V009)")
	void photoHabitWithoutSession_throws() {
		// Given
		givenHabit(HabitVerificationType.PHOTO, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.PHOTO_REQUIRED);
	}

	@Test
	@DisplayName("세션을 찾을 수 없음 — UPLOAD_SESSION_NOT_FOUND(V004)")
	void sessionNotFound_throws() {
		// Given
		givenHabit(HabitVerificationType.PHOTO, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitUploadSessionPort.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, 99L, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("크루용 세션(crewId NOT NULL) 사용 — UPLOAD_SESSION_CREW_MISMATCH(V016)")
	void crewSession_throws() {
		// Given
		givenHabit(HabitVerificationType.PHOTO, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitUploadSessionPort.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.of(
				new UploadSessionInfo(99L, "crew-1", null, false, true, FIXED_NOW, "key")));

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, 99L, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_CREW_MISMATCH);
	}

	@Test
	@DisplayName("다른 습관용 세션 사용 — HABIT_UPLOAD_SESSION_MISMATCH(HB009)")
	void otherHabitSession_throws() {
		// Given
		givenHabit(HabitVerificationType.PHOTO, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitUploadSessionPort.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.of(
				new UploadSessionInfo(99L, null, "other-habit", false, true, FIXED_NOW, "key")));

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, 99L, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.HABIT_UPLOAD_SESSION_MISMATCH);
	}

	@Test
	@DisplayName("PHOTO 인증 정상 — 세션 완료·본인 소유·마감 전이면 성공한다")
	void photoVerification_success() {
		// Given
		givenHabit(HabitVerificationType.PHOTO, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitUploadSessionPort.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.of(
				new UploadSessionInfo(99L, null, HABIT_ID, false, true, FIXED_NOW, "upload-key")));
		given(storagePort.getImageUrl(anyString())).willReturn("https://s3.example.com/image.jpg");
		given(habitVerificationRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitVerificationResponse result = service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, 99L, null));

		// Then
		assertThat(result.imageUrl()).isEqualTo("https://s3.example.com/image.jpg");
		assertThat(result.cycle().completedDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("TEXT 인증 — 마감 + 3분(grace 5분 이내) → 성공")
	void textDeadlineWithinGrace_success() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.minusMinutes(3));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitVerificationRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		HabitVerificationResponse result = service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트"));

		// Then
		assertThat(result).isNotNull();
	}

	@Test
	@DisplayName("TEXT 인증 — 마감 + 6분(grace 5분 초과) → VERIFICATION_DEADLINE_EXCEEDED")
	void textDeadlineExceedsGrace_throws() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.minusMinutes(6));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("자정 직후 건너뛴 날 인증(슬롯 불일치, D12) — VERIFICATION_DEADLINE_EXCEEDED(V002)")
	void skippedDaySlotMismatch_throws() {
		// Given — 이틀 전 시작했는데 completedDays=0(1일차 미인증인 채 방치) → 기대 슬롯은 이틀 전, 오늘은 그로부터 2일 뒤
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY.minusDays(2), 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("자정 넘긴 grace 인증(00:02, targetDate=다음 날) — 슬롯 가드가 날짜 경계를 포섭해 V002")
	void graceAfterMidnight_slotMismatch_throws() {
		// Given — Clock을 자정 2분 후로 고정. 어제 슬롯(startDate+completedDays)은 아직 미인증인데
		// LocalDate.now(clock)은 이미 다음 날로 넘어가 있어 슬롯 불일치가 발생한다
		LocalDateTime justAfterMidnight = LocalDateTime.of(2026, 7, 6, 0, 2, 0);
		Clock clockAfterMidnight = Clock.fixed(justAfterMidnight.atZone(ZONE).toInstant(), ZONE);
		service = new CreateHabitVerificationService(
				habitRepositoryPort, habitCycleRepositoryPort, habitVerificationRepositoryPort,
				habitUploadSessionPort, storagePort, clockAfterMidnight);

		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		LocalDate yesterday = justAfterMidnight.toLocalDate().minusDays(1);
		HabitCycle cycle = cycleOf(yesterday, 0, justAfterMidnight.plusDays(2));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_DEADLINE_EXCEEDED);
	}

	@Test
	@DisplayName("더블탭(유니크 제약 위반) — VERIFICATION_ALREADY_EXISTS(V003)로 명시 매핑된다(G2)")
	void doubleTapUniqueViolation_mapsToV003() {
		// Given
		givenHabit(HabitVerificationType.TEXT, HabitStatus.ACTIVE);
		HabitCycle cycle = cycleOf(TODAY, 0, FIXED_NOW.plusDays(1));
		given(habitCycleRepositoryPort.findByHabitIdAndStatus(HABIT_ID, HabitCycleStatus.IN_PROGRESS))
				.willReturn(Optional.of(cycle));
		given(habitVerificationRepositoryPort.existsByHabitIdAndTargetDate(HABIT_ID, TODAY)).willReturn(false);
		given(habitVerificationRepositoryPort.save(any()))
				.willThrow(new DataIntegrityViolationException("UK violation"));

		// When & Then
		assertThatThrownBy(() -> service.createVerification(
				new CreateHabitVerificationCommand(USER_ID, HABIT_ID, null, "텍스트")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ALREADY_EXISTS);
	}

	// --- 헬퍼 메서드 ---

	private void givenHabit(HabitVerificationType type, HabitStatus status) {
		Habit habit = Habit.of(HABIT_ID, USER_ID, "매일 습관", type,
				LocalTime.of(23, 59, 59), status, FIXED_NOW.minusDays(10), null);
		given(habitRepositoryPort.findByIdForUpdate(HABIT_ID)).willReturn(Optional.of(habit));
	}

	private HabitCycle cycleOf(LocalDate startDate, int completedDays, LocalDateTime deadline) {
		return HabitCycle.of(CYCLE_ID, HABIT_ID, USER_ID, 1, 3, completedDays,
				HabitCycleStatus.IN_PROGRESS, startDate, deadline, FIXED_NOW.minusDays(1));
	}
}
