package com.triagain.verification.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.verification.domain.vo.UploadSessionStatus;
import com.triagain.verification.port.in.UploadSessionQueryUseCase;
import com.triagain.verification.port.in.UploadSessionQueryUseCase.UploadSessionSnapshot;
import com.triagain.verification.port.out.SsePort;

/** ③ 소유권 검증 + emitter 등록 '후' 재조회 레이스 순서 — impl-guards G-1·G-2·G-5를 결정적으로 단언 */
@ExtendWith(MockitoExtension.class)
class SubscribeUploadSessionServiceTest {

	private static final Long SESSION_ID = 1L;
	private static final String USER_ID = "user-1";

	@Mock
	private UploadSessionQueryUseCase uploadSessionQueryUseCase;

	@Mock
	private SsePort ssePort;

	private SubscribeUploadSessionService subscribeUploadSessionService;

	@BeforeEach
	void setUp() {
		subscribeUploadSessionService = new SubscribeUploadSessionService(uploadSessionQueryUseCase, ssePort);
	}

	private static UploadSessionSnapshot snapshot(UploadSessionStatus status) {
		return new UploadSessionSnapshot(SESSION_ID, null, null, status, LocalDateTime.now(), "key.jpg");
	}

	@Test
	@DisplayName("S1: 1차 조회 PENDING → 2차 조회 COMPLETED면 완료 이벤트를 정확히 1회 보낸다(G-2)")
	void firstPendingThenCompleted_sendsOnce() {
		// Given — 등록 전후 사이에 완료 콜백이 끼어든 상황을 재조회 값 전환으로 재현
		given(uploadSessionQueryUseCase.getOwnedOrThrow(SESSION_ID, USER_ID))
				.willReturn(snapshot(UploadSessionStatus.PENDING), snapshot(UploadSessionStatus.COMPLETED));

		// When
		subscribeUploadSessionService.subscribe(SESSION_ID, USER_ID);

		// Then
		verify(ssePort, times(1)).send(SESSION_ID, "COMPLETED");
	}

	@Test
	@DisplayName("S2: 1차·2차 모두 PENDING이면 완료 이벤트를 보내지 않는다")
	void bothPending_neverSends() {
		// Given
		given(uploadSessionQueryUseCase.getOwnedOrThrow(SESSION_ID, USER_ID))
				.willReturn(snapshot(UploadSessionStatus.PENDING), snapshot(UploadSessionStatus.PENDING));

		// When
		subscribeUploadSessionService.subscribe(SESSION_ID, USER_ID);

		// Then
		verify(ssePort, never()).send(any(), any());
	}

	@Test
	@DisplayName("S3: 1차·2차 모두 COMPLETED여도 완료 이벤트는 1회만 보낸다(중복 없음)")
	void bothCompleted_sendsOnce() {
		// Given
		given(uploadSessionQueryUseCase.getOwnedOrThrow(SESSION_ID, USER_ID))
				.willReturn(snapshot(UploadSessionStatus.COMPLETED), snapshot(UploadSessionStatus.COMPLETED));

		// When
		subscribeUploadSessionService.subscribe(SESSION_ID, USER_ID);

		// Then
		verify(ssePort, times(1)).send(SESSION_ID, "COMPLETED");
	}

	@Test
	@DisplayName("S4: 소유권 검증 실패 시 emitter 등록 자체를 하지 않는다(G-1, 불변식 ② 결정적 단언)")
	void ownershipFails_neverSubscribes() {
		// Given
		given(uploadSessionQueryUseCase.getOwnedOrThrow(SESSION_ID, USER_ID))
				.willThrow(new BusinessException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

		// When & Then
		assertThatThrownBy(() -> subscribeUploadSessionService.subscribe(SESSION_ID, USER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_FOUND);

		verify(ssePort, never()).subscribe(any());
	}

	@Test
	@DisplayName("S5: 2차 조회 결과가 EXPIRED면 완료 이벤트를 보내지 않는다(G-5, EXPIRED는 SSE로 싣지 않는다)")
	void secondQueryExpired_neverSends() {
		// Given
		given(uploadSessionQueryUseCase.getOwnedOrThrow(SESSION_ID, USER_ID))
				.willReturn(snapshot(UploadSessionStatus.PENDING), snapshot(UploadSessionStatus.EXPIRED));

		// When
		subscribeUploadSessionService.subscribe(SESSION_ID, USER_ID);

		// Then
		verify(ssePort, never()).send(any(), any());
	}
}
