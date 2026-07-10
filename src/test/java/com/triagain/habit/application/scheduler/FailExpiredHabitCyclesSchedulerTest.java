package com.triagain.habit.application.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.habit.domain.model.HabitCycle;
import com.triagain.habit.domain.vo.HabitCycleStatus;
import com.triagain.habit.port.out.HabitCycleRepositoryPort;

@ExtendWith(MockitoExtension.class)
class FailExpiredHabitCyclesSchedulerTest {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 5, 0, 10, 0);
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW.atZone(ZONE).toInstant(), ZONE);

	@Mock
	private HabitCycleRepositoryPort habitCycleRepositoryPort;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private DeadLetterRepositoryPort deadLetterRepositoryPort;

	private FailExpiredHabitCyclesScheduler scheduler;

	@BeforeEach
	void setUp() {
		lenient().doAnswer(invocation -> {
			invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());

		lenient().doAnswer(invocation -> {
			org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		}).when(transactionTemplate).execute(any());

		ChunkProcessor chunkProcessor = new ChunkProcessor(transactionTemplate);
		scheduler = new FailExpiredHabitCyclesScheduler(
				habitCycleRepositoryPort, chunkProcessor, deadLetterRepositoryPort, FIXED_CLOCK);
	}

	@Test
	@DisplayName("만료 사이클 없으면 save 호출 없음")
	void noExpiredCycles_noSave() {
		// Given
		given(habitCycleRepositoryPort.findExpiredWithoutVerification(FIXED_NOW))
				.willReturn(Collections.emptyList());

		// When
		scheduler.failExpiredHabitCycles();

		// Then
		verify(habitCycleRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("만료 사이클 → FAILED 처리된다")
	void expiredCycle_failed() {
		// Given
		HabitCycle expired = inProgressCycle("HCYC-1", "habit-1", "user-1", 0);

		given(habitCycleRepositoryPort.findExpiredWithoutVerification(FIXED_NOW))
				.willReturn(List.of(expired));
		given(habitCycleRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		scheduler.failExpiredHabitCycles();

		// Then
		assertThat(expired.getStatus()).isEqualTo(HabitCycleStatus.FAILED);
		verify(habitCycleRepositoryPort, times(1)).save(any());
	}

	@Test
	@DisplayName("1건 실패해도 나머지는 정상 처리되고 Dead Letter가 기록된다")
	void oneFailure_doesNotAffectOthers_andDeadLetterSaved() {
		// Given
		HabitCycle expired1 = inProgressCycle("HCYC-1", "habit-1", "user-1", 0);
		HabitCycle expired2 = inProgressCycle("HCYC-2", "habit-2", "user-2", 1);
		HabitCycle freshExpired1 = inProgressCycle("HCYC-1", "habit-1", "user-1", 0);

		given(habitCycleRepositoryPort.findExpiredWithoutVerification(FIXED_NOW))
				.willReturn(List.of(expired1, expired2));
		given(habitCycleRepositoryPort.findById("HCYC-1")).willReturn(Optional.of(freshExpired1));
		given(habitCycleRepositoryPort.save(any()))
				.willThrow(new RuntimeException("DB error"))
				.willAnswer(inv -> inv.getArgument(0))
				.willAnswer(inv -> inv.getArgument(0));

		// When & Then — 예외 전파 없음
		assertThatCode(() -> scheduler.failExpiredHabitCycles()).doesNotThrowAnyException();

		assertThat(freshExpired1.getStatus()).isEqualTo(HabitCycleStatus.FAILED);
	}

	@Test
	@DisplayName(":now 파라미터로 앱 Clock(주입) 시각을 바인딩한다 — DB NOW() 미사용")
	void usesInjectedClock() {
		// Given
		given(habitCycleRepositoryPort.findExpiredWithoutVerification(FIXED_NOW))
				.willReturn(Collections.emptyList());

		// When
		scheduler.failExpiredHabitCycles();

		// Then
		verify(habitCycleRepositoryPort).findExpiredWithoutVerification(FIXED_NOW);
	}

	private static HabitCycle inProgressCycle(String id, String habitId, String userId, int completedDays) {
		return HabitCycle.of(id, habitId, userId, 1, 3, completedDays,
				HabitCycleStatus.IN_PROGRESS, LocalDate.of(2026, 7, 1),
				LocalDateTime.of(2026, 7, 4, 23, 59, 59), LocalDateTime.now());
	}
}
