package com.triagain.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadLetterTest {

	@Test
	@DisplayName("retry 호출 시 retryCount가 증가하고 nextRetryAt이 지수 백오프로 재계산된다")
	void retry_incrementsCountAndUpdatesNextRetryAt() {
		// Given
		DeadLetter dl = DeadLetter.of(DeadLetterTaskType.CHALLENGE_FAIL, "target-1", "error");

		// When
		dl.retry();

		// Then
		assertThat(dl.getRetryCount()).isEqualTo(1);
		assertThat(dl.getStatus()).isEqualTo(DeadLetterStatus.PENDING);
		assertThat(dl.getNextRetryAt()).isNotNull();
	}

	@Test
	@DisplayName("maxRetries 도달 시 retry 호출하면 ABANDONED 상태로 전환된다")
	void retry_atMaxRetries_becomesAbandoned() {
		// Given — maxRetries=3이므로 3번 retry하면 ABANDONED
		DeadLetter dl = DeadLetter.of(DeadLetterTaskType.CHALLENGE_FAIL, "target-1", "error");

		// When
		dl.retry(); // retryCount=1
		dl.retry(); // retryCount=2
		dl.retry(); // retryCount=3 >= maxRetries(3) → ABANDONED

		// Then
		assertThat(dl.getRetryCount()).isEqualTo(3);
		assertThat(dl.getStatus()).isEqualTo(DeadLetterStatus.ABANDONED);
	}

	@Test
	@DisplayName("ABANDONED 상태에서 retry 호출 시 IllegalStateException 발생")
	void retry_whenAbandoned_throwsException() {
		// Given
		DeadLetter dl = DeadLetter.of(DeadLetterTaskType.CHALLENGE_FAIL, "target-1", "error");
		dl.retry();
		dl.retry();
		dl.retry(); // ABANDONED

		// When & Then
		assertThatThrownBy(dl::retry)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PENDING 상태만 재시도 가능");
	}

	@Test
	@DisplayName("resolve 호출 시 PENDING → RESOLVED 상태로 전환된다")
	void resolve_pendingToResolved() {
		// Given
		DeadLetter dl = DeadLetter.of(DeadLetterTaskType.SESSION_EXPIRE, "target-1", "error");

		// When
		dl.resolve();

		// Then
		assertThat(dl.getStatus()).isEqualTo(DeadLetterStatus.RESOLVED);
	}

	@Test
	@DisplayName("RESOLVED 상태에서 resolve 호출 시 IllegalStateException 발생")
	void resolve_whenResolved_throwsException() {
		// Given
		DeadLetter dl = DeadLetter.of(DeadLetterTaskType.SESSION_EXPIRE, "target-1", "error");
		dl.resolve();

		// When & Then
		assertThatThrownBy(dl::resolve)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PENDING 상태만 해결 가능");
	}

	@Test
	@DisplayName("RESOLVED 상태에서 retry 호출 시 IllegalStateException 발생")
	void retry_whenResolved_throwsException() {
		// Given
		DeadLetter dl = DeadLetter.of(DeadLetterTaskType.SESSION_EXPIRE, "target-1", "error");
		dl.resolve();

		// When & Then
		assertThatThrownBy(dl::retry)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PENDING 상태만 재시도 가능");
	}
}
