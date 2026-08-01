package com.triagain.crew.application.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.triagain.common.domain.DeadLetter;
import com.triagain.common.domain.DeadLetterTaskType;
import com.triagain.common.port.out.DeadLetterRepositoryPort;
import com.triagain.common.scheduler.ChunkProcessor;
import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.vo.ChallengeStatus;
import com.triagain.crew.port.out.ChallengeRepositoryPort;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.crew.port.out.NotificationPort;

@ExtendWith(MockitoExtension.class)
class FailExpiredChallengesSchedulerTest {

	@Mock
	private ChallengeRepositoryPort challengeRepositoryPort;

	@Mock
	private CrewRepositoryPort crewRepositoryPort;

	@Mock
	private NotificationPort notificationPort;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private DeadLetterRepositoryPort deadLetterRepositoryPort;

	private FailExpiredChallengesScheduler scheduler;

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
		scheduler = new FailExpiredChallengesScheduler(challengeRepositoryPort, crewRepositoryPort, notificationPort, chunkProcessor, deadLetterRepositoryPort);
	}

	@Test
	@DisplayName("만료 챌린지 없으면 실패 전환 호출 없음")
	void noExpiredChallenges_noUpdate() {
		// Given
		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(Collections.emptyList());

		// When
		scheduler.failExpiredChallenges();

		// Then
		verify(challengeRepositoryPort, never()).failIfUnchanged(any(), anyInt());
	}

	@Test
	@DisplayName("만료 챌린지 → FAILED 처리만 수행 (새 챌린지 미생성)")
	void expiredChallenge_failedOnly() {
		// Given
		Challenge expired = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(expired));
		given(challengeRepositoryPort.failIfUnchanged("CHAL-1", 0)).willReturn(1);

		// When
		scheduler.failExpiredChallenges();

		// Then — FAILED 처리, 원본 스냅샷 completed_days(0) 기준으로 조건부 UPDATE 1회
		assertThat(expired.getStatus()).isEqualTo(ChallengeStatus.FAILED);
		verify(challengeRepositoryPort, times(1)).failIfUnchanged("CHAL-1", 0);
	}

	@Test
	@DisplayName("여러 챌린지 동시 실패 처리")
	void multipleChallenges_allFailed() {
		// Given
		Challenge expired1 = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);
		Challenge expired2 = inProgressChallenge("CHAL-2", "user-2", "crew-2", 2, 1);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(expired1, expired2));
		given(challengeRepositoryPort.failIfUnchanged("CHAL-1", 0)).willReturn(1);
		given(challengeRepositoryPort.failIfUnchanged("CHAL-2", 1)).willReturn(1);

		// When
		scheduler.failExpiredChallenges();

		// Then — 2건 모두 FAILED, 조건부 UPDATE 2회
		assertThat(expired1.getStatus()).isEqualTo(ChallengeStatus.FAILED);
		assertThat(expired2.getStatus()).isEqualTo(ChallengeStatus.FAILED);
		verify(challengeRepositoryPort, times(2)).failIfUnchanged(any(), anyInt());
	}

	@Test
	@DisplayName("청크 처리가 실패해도 건별 재시도로 복구되면 Dead Letter를 남기지 않는다")
	void oneFailure_doesNotAffectOthers_andNoDeadLetter() {
		// Given
		Challenge expired1 = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);
		Challenge expired2 = inProgressChallenge("CHAL-2", "user-2", "crew-2", 2, 1);

		// rehydrator용 fresh 인스턴스
		Challenge freshExpired1 = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);
		Challenge freshExpired2 = inProgressChallenge("CHAL-2", "user-2", "crew-2", 2, 1);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(expired1, expired2));
		given(challengeRepositoryPort.findById("CHAL-1")).willReturn(Optional.of(freshExpired1));
		given(challengeRepositoryPort.findById("CHAL-2")).willReturn(Optional.of(freshExpired2));
		given(challengeRepositoryPort.failIfUnchanged(any(), anyInt()))
				.willThrow(new RuntimeException("DB error"))    // 청크 내 첫 조건부 UPDATE 실패
				.willReturn(1)                                   // per-item retry 성공
				.willReturn(1);

		// When & Then — 예외 전파 없음
		assertThatCode(() -> scheduler.failExpiredChallenges())
				.doesNotThrowAnyException();

		// rehydrate된 fresh 인스턴스가 FAILED로 처리됨
		assertThat(freshExpired1.getStatus()).isEqualTo(ChallengeStatus.FAILED);
		assertThat(freshExpired2.getStatus()).isEqualTo(ChallengeStatus.FAILED);

		// 실패 건은 Dead Letter에 기록되지 않음 (rehydrate 후 재시도 성공)
		verify(deadLetterRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("정기 스케줄러는 전량 스캔으로 만료 챌린지 실패 처리")
	void failExpiredChallenges_usesFullScan() {
		// Given
		Challenge expired = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(expired));
		given(challengeRepositoryPort.failIfUnchanged("CHAL-1", 0)).willReturn(1);

		// When
		scheduler.failExpiredChallenges();

		// Then — 전량 스캔 사용, FAILED 처리
		verify(challengeRepositoryPort).findExpiredWithoutVerification();
		assertThat(expired.getStatus()).isEqualTo(ChallengeStatus.FAILED);
	}

	@Test
	@DisplayName("스킵된 챌린지는 실패 알림 대상에서 제외된다")
	void skippedChallenge_isExcludedFromNotificationTargets() {
		// Given — 서로 다른 크루의 2건: 1건은 정상 실패 전환(1), 1건은 조건 불일치로 스킵(0)
		Challenge failedChallenge = inProgressChallenge("CHAL-X", "user-x", "crew-x", 1, 0);
		Challenge skippedChallenge = inProgressChallenge("CHAL-Y", "user-y", "crew-y", 1, 0);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(failedChallenge, skippedChallenge));
		given(challengeRepositoryPort.failIfUnchanged("CHAL-X", 0)).willReturn(1);
		given(challengeRepositoryPort.failIfUnchanged("CHAL-Y", 0)).willReturn(0);
		given(crewRepositoryPort.findAllByIds(any())).willReturn(Collections.emptyList());

		// When
		scheduler.failExpiredChallenges();

		// Then — 알림 준비 대상 crewId에 스킵된 챌린지의 크루("crew-y")는 담기지 않는다
		// (실제 알림 발송은 코드상 비활성 상태라 대상 리스트 관측으로 검증한다)
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> crewIdsCaptor = ArgumentCaptor.forClass(List.class);
		verify(crewRepositoryPort).findAllByIds(crewIdsCaptor.capture());
		assertThat(crewIdsCaptor.getValue()).containsExactly("crew-x");
	}

	@Test
	@DisplayName("전부 스킵되면 알림 준비 자체를 하지 않는다")
	void allSkipped_neverPreparesNotifications() {
		// Given — 둘 다 조건 불일치로 스킵(0)
		Challenge skipped1 = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);
		Challenge skipped2 = inProgressChallenge("CHAL-2", "user-2", "crew-2", 2, 1);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(skipped1, skipped2));
		given(challengeRepositoryPort.failIfUnchanged("CHAL-1", 0)).willReturn(0);
		given(challengeRepositoryPort.failIfUnchanged("CHAL-2", 1)).willReturn(0);

		// When
		scheduler.failExpiredChallenges();

		// Then
		verify(crewRepositoryPort, never()).findAllByIds(any());
	}

	@Test
	@DisplayName("rehydrate 결과가 이미 SUCCESS면 Dead Letter에 쌓지 않는다")
	void rehydratedAlreadySuccess_doesNotSaveDeadLetter() {
		// Given — 청크 첫 조건부 UPDATE 시도에서 예외 발생 → 재시도 유도, rehydrate 결과는 이미 SUCCESS
		Challenge expired = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 2);
		Challenge rehydratedSuccess = challengeFixture("CHAL-1", "user-1", "crew-1", 1, 3, ChallengeStatus.SUCCESS);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(expired));
		given(challengeRepositoryPort.failIfUnchanged(any(), anyInt()))
				.willThrow(new RuntimeException("동시 처리 충돌"));
		given(challengeRepositoryPort.findById("CHAL-1")).willReturn(Optional.of(rehydratedSuccess));

		// When & Then — 예외 전파 없음 (SUCCESS면 실패 전환 시도 자체를 건너뜀)
		assertThatCode(() -> scheduler.failExpiredChallenges())
				.doesNotThrowAnyException();

		verify(deadLetterRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("재시도 경로에서도 기대치는 원본 스냅샷 값을 사용한다 (rehydrate된 최신값이 아니라)")
	void retryPath_usesOriginalSnapshotAsExpectedValue_notRehydratedValue() {
		// Given — A(스냅샷 completed_days=1), 같은 청크의 B가 먼저 처리되며 예외를 던져 청크 전체가
		// 실패하고 건별 재시도가 유발된다. 재시도 시 rehydrator가 A를 completed_days=2로 반환한다.
		Challenge challengeA = inProgressChallenge("CHAL-A", "user-a", "crew-a", 1, 1);
		Challenge challengeB = inProgressChallenge("CHAL-B", "user-b", "crew-b", 1, 0);
		Challenge rehydratedA = inProgressChallenge("CHAL-A", "user-a", "crew-a", 1, 2);
		Challenge rehydratedB = inProgressChallenge("CHAL-B", "user-b", "crew-b", 1, 0);

		// B를 먼저 처리시켜(리스트 순서) 청크 첫 시도에서 A가 손대지지 않은 채로 예외가 나게 한다
		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(challengeB, challengeA));
		given(challengeRepositoryPort.failIfUnchanged("CHAL-B", 0))
				.willThrow(new RuntimeException("동시 처리 충돌"));
		given(challengeRepositoryPort.findById("CHAL-A")).willReturn(Optional.of(rehydratedA));
		given(challengeRepositoryPort.findById("CHAL-B")).willReturn(Optional.of(rehydratedB));

		// When
		scheduler.failExpiredChallenges();

		// Then — A의 기대치는 원본 스냅샷(1)이어야 한다. rehydrate된 값(2)을 쓰면 이 검증이 깨진다.
		verify(challengeRepositoryPort).failIfUnchanged("CHAL-A", 1);
		verify(challengeRepositoryPort, never()).failIfUnchanged("CHAL-A", 2);
	}

	@Test
	@DisplayName("재시도까지 실패하면 Dead Letter에 유형·대상·사유가 기록된다")
	void retryAlsoFails_savesDeadLetterWithTaskTypeAndTarget() {
		// Given — 조건부 UPDATE가 계속 실패하고, rehydrate 결과도 여전히 IN_PROGRESS라 재시도에서 또 터진다
		Challenge expired = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);
		Challenge rehydratedInProgress = inProgressChallenge("CHAL-1", "user-1", "crew-1", 1, 0);

		given(challengeRepositoryPort.findExpiredWithoutVerification())
				.willReturn(List.of(expired));
		given(challengeRepositoryPort.failIfUnchanged(any(), anyInt()))
				.willThrow(new RuntimeException("DB 연결 끊김"));
		given(challengeRepositoryPort.findById("CHAL-1")).willReturn(Optional.of(rehydratedInProgress));

		// When & Then — 예외는 전파되지 않는다
		assertThatCode(() -> scheduler.failExpiredChallenges())
				.doesNotThrowAnyException();

		ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
		verify(deadLetterRepositoryPort).save(captor.capture());
		DeadLetter saved = captor.getValue();
		assertThat(saved.getTaskType()).isEqualTo(DeadLetterTaskType.CHALLENGE_FAIL);
		assertThat(saved.getTargetId()).isEqualTo("CHAL-1");
		assertThat(saved.getErrorMessage()).isEqualTo("DB 연결 끊김");
	}

	// ─────────────────────────────────────────────────────────────
	// 테스트 헬퍼
	// ─────────────────────────────────────────────────────────────

	private static Challenge inProgressChallenge(
			String id, String userId, String crewId, int cycleNumber, int completedDays) {
		return challengeFixture(id, userId, crewId, cycleNumber, completedDays, ChallengeStatus.IN_PROGRESS);
	}

	private static Challenge challengeFixture(
			String id, String userId, String crewId, int cycleNumber, int completedDays, ChallengeStatus status) {
		return Challenge.of(id, userId, crewId, cycleNumber, 3, completedDays, status,
				LocalDate.of(2026, 3, 1), LocalDateTime.of(2026, 3, 4, 23, 59, 59), LocalDateTime.now());
	}
}
