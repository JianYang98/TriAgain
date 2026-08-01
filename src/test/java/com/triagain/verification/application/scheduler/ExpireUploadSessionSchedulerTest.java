package com.triagain.verification.application.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;


@ExtendWith(MockitoExtension.class)
class ExpireUploadSessionSchedulerTest {

	@Mock
	private UploadSessionRepositoryPort uploadSessionRepositoryPort;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private DeadLetterRepositoryPort deadLetterRepositoryPort;

	private ExpireUploadSessionScheduler scheduler;

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
		scheduler = new ExpireUploadSessionScheduler(uploadSessionRepositoryPort, chunkProcessor, deadLetterRepositoryPort);
	}

	@Test
	@DisplayName("만료 대상 세션 없으면 save 호출 없음")
	void noPendingSessions_noSave() {
		// Given
		given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
				.willReturn(Collections.emptyList());

		// When
		scheduler.expirePendingSessions();

		// Then
		verify(uploadSessionRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("PENDING 세션이 EXPIRED로 전환된다")
	void pendingSession_expiredSuccessfully() {
		// Given
		UploadSession session = pendingSession(1L);

		given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
				.willReturn(List.of(session));
		given(uploadSessionRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		scheduler.expirePendingSessions();

		// Then
		assertThat(session.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
		verify(uploadSessionRepositoryPort, times(1)).save(any());
	}

	@Test
	@DisplayName("여러 세션 동시 만료 처리")
	void multipleSessions_allExpired() {
		// Given
		UploadSession session1 = pendingSession(1L);
		UploadSession session2 = pendingSession(2L);

		given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
				.willReturn(List.of(session1, session2));
		given(uploadSessionRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

		// When
		scheduler.expirePendingSessions();

		// Then
		assertThat(session1.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
		assertThat(session2.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
		verify(uploadSessionRepositoryPort, times(2)).save(any());
	}

	@Test
	@DisplayName("청크 저장이 실패해도 개별 재시도가 성공하면 Dead Letter는 기록되지 않는다")
	void chunkFails_retrySucceeds_noDeadLetter() {
		// Given
		UploadSession session1 = pendingSession(1L);
		UploadSession session2 = pendingSession(2L);

		// rehydrator용 fresh 인스턴스
		UploadSession freshSession1 = pendingSession(1L);
		UploadSession freshSession2 = pendingSession(2L);

		given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
				.willReturn(List.of(session1, session2));
		given(uploadSessionRepositoryPort.findById(1L)).willReturn(Optional.of(freshSession1));
		given(uploadSessionRepositoryPort.findById(2L)).willReturn(Optional.of(freshSession2));
		given(uploadSessionRepositoryPort.save(any()))
				.willThrow(new RuntimeException("DB error"))    // 청크 내 첫 save 실패
				.willAnswer(inv -> inv.getArgument(0))          // per-item retry 성공
				.willAnswer(inv -> inv.getArgument(0));

		// When & Then — 예외 전파 없음
		assertThatCode(() -> scheduler.expirePendingSessions())
				.doesNotThrowAnyException();

		// rehydrate된 fresh 인스턴스가 EXPIRED로 처리됨
		assertThat(freshSession1.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);
		assertThat(freshSession2.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);

		// rehydrate 후 재시도 성공이므로 Dead Letter 없음
		verify(deadLetterRepositoryPort, never()).save(any());
	}

	@Test
	@DisplayName("재시도까지 실패하면 그 건만 Dead Letter에 유형·대상·사유가 기록된다")
	void retryAlsoFails_savesDeadLetterWithTaskTypeAndTarget() {
		// Given — 2건 중 2번 세션만 재시도까지 실패한다
		LocalDateTime past = LocalDateTime.now().minusMinutes(20);
		UploadSession session1 = pendingSession(1L);
		UploadSession session2 = pendingSession(2L);

		// rehydrator용 fresh 인스턴스
		UploadSession freshSession1 = pendingSession(1L);
		UploadSession freshSession2 = pendingSession(2L);

		given(uploadSessionRepositoryPort.findPendingSessionsBefore(any(LocalDateTime.class)))
				.willReturn(List.of(session1, session2));
		given(uploadSessionRepositoryPort.findById(1L)).willReturn(Optional.of(freshSession1));
		given(uploadSessionRepositoryPort.findById(2L)).willReturn(Optional.of(freshSession2));
		// 메시지를 나눠 둔다 — 기록되는 사유가 청크 예외가 아니라 재시도 예외임을 고정하기 위해서다
		given(uploadSessionRepositoryPort.save(any()))
				.willThrow(new RuntimeException("청크 저장 실패"))   // 청크 전체 시도
				.willAnswer(inv -> inv.getArgument(0))             // 1번 세션 재시도 성공
				.willThrow(new RuntimeException("재시도 실패"));     // 2번 세션 재시도 실패

		// When & Then — 예외 전파 없음
		assertThatCode(() -> scheduler.expirePendingSessions())
				.doesNotThrowAnyException();

		// 재시도가 성공한 건은 EXPIRED 로 남는다 — 부분 실패가 전체를 망치지 않는다
		assertThat(freshSession1.getStatus()).isEqualTo(UploadSessionStatus.EXPIRED);

		// 실패한 건만 Dead Letter 로 간다. 대상이 2번인지까지 봐야 id 오기록을 잡는다
		ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
		verify(deadLetterRepositoryPort, times(1)).save(captor.capture());
		DeadLetter saved = captor.getValue();
		assertThat(saved.getTaskType()).isEqualTo(DeadLetterTaskType.SESSION_EXPIRE);
		assertThat(saved.getTargetId()).isEqualTo("2");
		assertThat(saved.getErrorMessage()).isEqualTo("재시도 실패");
	}

	// ─────────────────────────────────────────────────────────────
	// 테스트 헬퍼
	// ─────────────────────────────────────────────────────────────

	/** 만료 대상 PENDING 세션. 컨텐츠타입·시각은 어느 단언에도 쓰이지 않아 고정값이다 */
	private UploadSession pendingSession(long id) {
		LocalDateTime past = LocalDateTime.now().minusMinutes(20);
		return UploadSession.of(id, "user-" + id, "crew-" + id, "key-" + id, "image/jpeg",
				UploadSessionStatus.PENDING, past, past);
	}
}
